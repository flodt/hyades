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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.model.ScoreCardCheck;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Ignore
class DepsDevApiClientExternalIntegrationTest {
    @Inject
    DepsDevApiClient depsDevApiClient;

    @Test
    void fetchLatestVersion() {
        Optional<String> version = this.depsDevApiClient.fetchLatestVersion("pypi", "pandas");
        assertThat(version).isPresent();
    }

    @Test
    void fetchDependents() {
        Optional<Integer> dependents = this.depsDevApiClient.fetchDependents("pypi", "pandas", "2.3.1");
        assertThat(dependents).isPresent();
    }

    @Test
    void fetchSourceRepoProjectKey() {
        Optional<String> project = this.depsDevApiClient.fetchSourceRepoProjectKey("pypi", "pandas", "2.3.1");
        assertThat(project).isPresent();
    }

    @Test
    void fetchScorecardAndStarsForksForProject() {
        Optional<ComponentHealthMetaModel> maybeMetaModel = this.depsDevApiClient.fetchScorecardAndStarsForksForProject("github.com/pandas-dev/pandas");
        assertThat(maybeMetaModel).isPresent();

        ComponentHealthMetaModel metaModel = maybeMetaModel.get();
        assertThat(metaModel)
                .as("all top‑level fields should be non‑null")
                .extracting(
                        ComponentHealthMetaModel::getOpenIssues,
                        ComponentHealthMetaModel::getStars,
                        ComponentHealthMetaModel::getForks,
                        ComponentHealthMetaModel::getScoreCardReferenceVersion,
                        ComponentHealthMetaModel::getScoreCardTimestamp,
                        ComponentHealthMetaModel::getScoreCardScore
                )
                .doesNotContainNull();

        // — the checks list itself —
        assertThat(metaModel.getScoreCardChecks())
                .as("scoreCardChecks list must exist and not be empty")
                .isNotNull()
                .isNotEmpty();

        // — each check’s fields —
        assertThat(metaModel.getScoreCardChecks())
                .as("each ScoreCardCheck’s properties should be set")
                .allSatisfy(check ->
                        assertThat(check)
                                .extracting(
                                        ScoreCardCheck::getName,
                                        ScoreCardCheck::getScore,
                                        ScoreCardCheck::getDescription,
                                        ScoreCardCheck::getDocumentationUrl,
                                        ScoreCardCheck::getReason,
                                        ScoreCardCheck::getDetails
                                )
                                .doesNotContainNull()
                );
    }
}