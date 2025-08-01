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
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.persistence.model.Repository;
import org.dependencytrack.persistence.model.RepositoryType;
import org.dependencytrack.persistence.repository.RepoEntityRepository;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.repositories.health.api.GitHubApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class GitHubHealthMetaSourceCodeAnalyzer implements IHealthMetaSourceCodeAnalyzer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    GitHubApiClient gitHubApiClient;

    @Inject
    RepoEntityRepository repoEntityRepository;


    @Override
    public boolean isApplicable(String projectKey) {
        return projectKey.startsWith("github.com");
    }

    @Override
    public ComponentHealthMetaModel analyze(PackageURL packageURL, String projectKey) {
        Component component = new Component();
        component.setPurl(packageURL);
        ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(component);

        // Connect to GitHub
        Optional<Repository> maybeRepository = QuarkusTransaction
                .joiningExisting()
                .call(() -> repoEntityRepository.findEnabledRepositoriesByType(RepositoryType.GITHUB))
                .stream()
                .filter(repo -> Objects.equals(repo.getUrl(), GitHubApiClient.GITHUB_URL))
                .findFirst();
        if (maybeRepository.isEmpty()) {
            logger.warn("Could not find GitHub configuration.");
            return metaModel;
        }
        Repository configuration = maybeRepository.get();

        boolean connectionSuccessful = gitHubApiClient.connect(configuration);
        if (!connectionSuccessful) {
            logger.warn("Could not connect to GitHub.");
            return metaModel;
        }

        Optional<ComponentHealthMetaModel> maybeGitHubData = gitHubApiClient.fetchDataFromGitHub(projectKey);
        if (maybeGitHubData.isEmpty()) {
            logger.warn("Failed to fetch GitHub data for {}", packageURL);
            return metaModel;
        }
        maybeGitHubData.ifPresent(metaModel::mergeFrom);

        return metaModel;
    }
}
