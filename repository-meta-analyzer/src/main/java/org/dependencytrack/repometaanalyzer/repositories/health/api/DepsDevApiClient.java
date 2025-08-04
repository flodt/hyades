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
package org.dependencytrack.repometaanalyzer.repositories.health.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.model.ScoreCardCheck;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class DepsDevApiClient extends ApiClient {
    public Optional<String> fetchLatestVersion(String system, String name) {
        if (system == null || name == null) return Optional.empty();

        String url = "https://api.deps.dev/v3/systems/" + urlEncode(system) + "/packages/" + urlEncode(name);
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

    public Optional<Integer> fetchDependents(String system, String name, String version) {
        if (system == null || name == null || version == null) return Optional.empty();

        String url = "https://api.deps.dev/v3alpha/systems/" + urlEncode(system) + "/packages/" + urlEncode(name)
                + "/versions/" + urlEncode(version) + ":dependents";
        return requestParseJsonForResult(url, root -> Optional.of(root.path("dependentCount").asInt()));
    }

    public Optional<String> fetchSourceRepoProjectKey(String system, String name, String version) {
        if (system == null || name == null || version == null) return Optional.empty();

        String url = "https://api.deps.dev/v3/systems/" + urlEncode(system) + "/packages/" + urlEncode(name)
                + "/versions/" + urlEncode(version);
        // TODO: Write a better parser for extracting the actual source code repository from the API response
        // The current solution only considers explicit "SOURCE_REPO" related projects - they are known good, tracked by
        // deps.dev and definitely in the format that is needed to retrieve the Scorecard scores.
        // They could also be read from the "links" section, as in some cases, the SOURCE_REPO is not present in
        // related projects. Unfortunately, these "links" don't follow a uniform format.
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

    public Optional<ComponentHealthMetaModel> fetchScorecardAndStarsForksIssuesForProject(String project) {
        if (project == null) return Optional.empty();

        String url = "https://api.deps.dev/v3/projects/" + urlEncode(project);
        return requestParseJsonForResult(url, (root) -> {
            ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(null);

            metaModel.setOpenIssues(root.path("openIssuesCount").asInt());
            metaModel.setStars(root.path("starsCount").asInt());
            metaModel.setForks(root.path("forksCount").asInt());

            // If this project has no listed scorecard, we're done here
            if (!root.has("scorecard")) return Optional.of(metaModel);

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
