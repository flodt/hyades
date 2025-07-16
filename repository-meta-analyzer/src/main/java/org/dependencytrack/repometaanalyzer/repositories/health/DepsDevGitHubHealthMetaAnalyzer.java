/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.dependencytrack.repometaanalyzer.repositories.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.packageurl.PackageURL;
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.model.ScoreCardCheck;
import org.kohsuke.github.GitHub;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class DepsDevGitHubHealthMetaAnalyzer extends AbstractHealthMetaAnalyzer {
    private static final Map<String, String> SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM = Map.ofEntries(
            Map.entry(PackageURL.StandardTypes.NPM, "NPM"),
            Map.entry(PackageURL.StandardTypes.GOLANG, "GO"),
            Map.entry(PackageURL.StandardTypes.MAVEN, "MAVEN"),
            Map.entry(PackageURL.StandardTypes.PYPI, "PYPI"),
            Map.entry(PackageURL.StandardTypes.NUGET, "NUGET"),
            Map.entry(PackageURL.StandardTypes.CARGO, "CARGO"),
            Map.entry(PackageURL.StandardTypes.GEM, "RUBYGEMS")
    );

    @Override
    public boolean isApplicable(PackageURL packageURL) {
        return SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM.containsKey(packageURL.getType());
    }

    @Override
    public ComponentHealthMetaModel analyze(PackageURL packageURL) {
        Component component = new Component();
        component.setPurl(packageURL);
        ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(component);

        /*
         * We fetch the desired health metadata through a combination of deps.dev and the GitHub APIs.
         * We first get the package information from deps.dev, which contains a set of versions.
         * There, we pick the latest one and extract the source code repository from the information retrieved through
         * the GetVersion endpoint.
         * We can then get the corresponding project information including the OpenSSF Scorecard data from the project
         * endpoint, after which we can fill in the rest of the metadata from the GitHub API.
         * If the project is not hosted on GitHub, there is currently no way to fetch its metadata beyond what we get
         * from deps.dev.
         */

        String system = Optional
                .ofNullable(SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM.get(packageURL.getType()))
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported PURL type: " + packageURL.getType()));
        String name = packageURL.getName();

        // collect latest version
        Optional<String> maybeVersion = fetchLatestVersion(system, name);
        if (maybeVersion.isEmpty()) {
            logger.warn("Could not determine latest version for {}", packageURL);
            return metaModel;
        }
        String version = maybeVersion.get();

        // collect package information on this candidate version
        Optional<String> maybeProject = fetchSourceRepoProjectKey(system, name, version);
        if (maybeProject.isEmpty()) {
            logger.warn("Could not determine source code project for {}", packageURL);
            return metaModel;
        }
        String project = maybeProject.get();

        // Collect OpenSSF Scorecard for this project
        Optional<ComponentHealthMetaModel> maybeScorecardStarsForks = fetchScorecardAndStarsForksForProject(project);
        if (maybeScorecardStarsForks.isEmpty()) {
            logger.warn("Could not determine scorecard for {}", packageURL);
            // we can continue with the GitHub API even without the scorecard; fallthrough
        }
        maybeScorecardStarsForks.ifPresent(metaModel::mergeFrom);

        // The rest is dependent on the GitHub API
        if (!project.startsWith("github.com")) {
            logger.warn("Source code project for {} is not on GitHub, can not fetch repository metadata.", packageURL);
            return metaModel;
        }

        return metaModel;
    }

    private Optional<String> fetchLatestVersion(String system, String name) {
        String url = "https://api.deps.dev/v3/systems" + urlEncode(system) + "/packages/" + urlEncode(name);
        return requestParseJsonForResult(url, (root) -> {
            JsonNode versionsNode = root.get("versions");
            return StreamSupport
                    .stream(versionsNode.spliterator(), false)
                    .filter(n -> n.path("isDefault").asBoolean(false))
                    .map(n -> n.path("versionKey").path("version").asText(null))
                    .filter(Objects::nonNull)
                    .findFirst();
        });
    }

    private Optional<String> fetchSourceRepoProjectKey(String system, String name, String version) {
        String url = "https://api.deps.dev/v3/systems" + urlEncode(system) + "/packages/" + urlEncode(name)
                + "/versions/" + urlEncode(version);
        return requestParseJsonForResult(url, (root) -> {
            JsonNode relatedProjectsNode = root.get("relatedProjects");
            return StreamSupport
                    .stream(relatedProjectsNode.spliterator(), false)
                    .filter(n -> "SOURCE_REPO".equals(n.path("relationType").asText()))
                    .map(n -> n.path("projectKey").path("id").asText())
                    .filter(Objects::nonNull)
                    .findFirst();
        });
    }

    private Optional<ComponentHealthMetaModel> fetchScorecardAndStarsForksForProject(String project) {
        String url = "https://api.deps.dev/v3/projects/" + urlEncode(project);
        return requestParseJsonForResult(url, (root) -> {
            ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(null);

            metaModel.setOpenIssues(root.path("openIssuesCount").asInt());
            metaModel.setStars(root.path("starsCount").asInt());
            metaModel.setForks(root.path("forksCount").asInt());
            metaModel.setScoreCardReferenceVersion(
                    root.path("scorecard").path("scorecard").path("version").asText()
            );
            metaModel.setScoreCardTimestamp(Instant.parse(root.path("scorecard").path("date").asText()));
            metaModel.setScoreCardScore((float) root.path("scorecard").path("overallScore").asDouble());

            List<ScoreCardCheck> scoreCardChecks = StreamSupport
                    .stream(root.path("scorecard").path("checks").spliterator(), false)
                    .map(node -> {
                        ScoreCardCheck check = new ScoreCardCheck();
                        check.setName(node.path("name").asText());
                        check.setScore((float) node.path("score").asDouble());
                        check.setDescription(node.path("documentation").path("shortDescription").asText());
                        check.setDocumentationUrl(node.path("documentation").path("url").asText());
                        check.setReason(node.path("reason").asText());
                        check.setDetails(
                                StreamSupport
                                        .stream(node.path("details").spliterator(), false)
                                        .map(JsonNode::asText)
                                        .toList()
                        );
                        return check;
                    })
                    .toList();
            metaModel.setScoreCardChecks(scoreCardChecks);

            return Optional.of(metaModel);
        });
    }
}
