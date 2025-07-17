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

import com.github.packageurl.PackageURL;
import jakarta.inject.Inject;
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.persistence.model.Repository;
import org.dependencytrack.persistence.model.RepositoryType;
import org.dependencytrack.persistence.repository.RepoEntityRepository;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient;
import org.dependencytrack.repometaanalyzer.repositories.health.api.GitHubApiClient;
import org.kohsuke.github.GitHub;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    @Inject
    RepoEntityRepository repoEntityRepository;

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

        DepsDevApiClient depsDevApiClient = new DepsDevApiClient(httpClient);

        // collect latest version
        Optional<String> maybeVersion = depsDevApiClient.fetchLatestVersion(system, name);
        if (maybeVersion.isEmpty()) {
            logger.warn("Could not determine latest version for {}", packageURL);
            return metaModel;
        }
        String version = maybeVersion.get();

        // Collect dependents count for this package version candidate
        Optional<Integer> maybeDependents = depsDevApiClient.fetchDependents(system, name, version);
        if (maybeDependents.isEmpty()) {
            logger.warn("Could not determine dependents for {}", packageURL);
            // fallthrough
        }
        maybeDependents.ifPresent(metaModel::setDependents);

        // collect package information on this candidate version
        Optional<String> maybeProject = depsDevApiClient.fetchSourceRepoProjectKey(system, name, version);
        if (maybeProject.isEmpty()) {
            logger.warn("Could not determine source code project for {}", packageURL);
            return metaModel;
        }
        String project = maybeProject.get();

        // Collect OpenSSF Scorecard for this project
        Optional<ComponentHealthMetaModel> maybeScorecardStarsForks = depsDevApiClient.fetchScorecardAndStarsForksForProject(project);
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

        // Connect to GitHub
        Optional<Repository> maybeRepository = repoEntityRepository
                .findEnabledRepositoriesByType(RepositoryType.GITHUB)
                .stream()
                .filter(repo -> Objects.equals(repo.getUrl(), GitHubApiClient.GITHUB_URL))
                .findFirst();
        if (maybeRepository.isEmpty()) {
            logger.warn("Could not find GitHub configuration.");
            return metaModel;
        }
        Repository configuration = maybeRepository.get();

        GitHubApiClient gitHubApiClient = new GitHubApiClient(httpClient);
        Optional<GitHub> maybeGitHub = gitHubApiClient.connectToGitHubWith(configuration);
        if (maybeGitHub.isEmpty()) {
            logger.warn("Could not connect to GitHub.");
            return metaModel;
        }
        GitHub gitHub = maybeGitHub.get();

        Optional<ComponentHealthMetaModel> maybeGitHubData = gitHubApiClient.fetchDataFromGitHub(gitHub, project);
        if (maybeGitHubData.isEmpty()) {
            logger.warn("Failed to fetch GitHub data for {}", packageURL);
            return metaModel;
        }
        maybeGitHubData.ifPresent(metaModel::mergeFrom);

        return metaModel;
    }
}
