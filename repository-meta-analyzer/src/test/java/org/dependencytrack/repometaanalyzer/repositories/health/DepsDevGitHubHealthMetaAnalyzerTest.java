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
import org.dependencytrack.persistence.model.Repository;
import org.dependencytrack.persistence.model.RepositoryType;
import org.dependencytrack.persistence.repository.RepoEntityRepository;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient;
import org.dependencytrack.repometaanalyzer.repositories.health.api.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DepsDevGitHubHealthMetaAnalyzerTest {

    private RepoEntityRepository repoEntityRepository;
    private DepsDevGitHubHealthMetaAnalyzer analyzer;

    private static final String GITHUB_URL = "https://github.com";

    @BeforeEach
    void setUp() {
        repoEntityRepository = mock(RepoEntityRepository.class);
        analyzer = new DepsDevGitHubHealthMetaAnalyzer();
        // inject our mock repo repoEntityRepository
        analyzer.repoEntityRepository = repoEntityRepository;
    }

    @Test
    void analyze_whenAllApisSucceed_mergesEverything() throws Exception {
        // Prepare a stub Repository so GitHubApiClient will be constructed with it
        Repository ghConfig = new Repository();
        ghConfig.setUrl(GITHUB_URL);
        when(repoEntityRepository.findEnabledRepositoriesByType(RepositoryType.GITHUB)).thenReturn(List.of(ghConfig));

        // Stub out DepsDevApiClient construction
        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            // stub fetchLatestVersion(...)
            when(mock.fetchLatestVersion("NPM", "left-pad")).thenReturn(Optional.of("1.2.3"));
            // stub fetchDependents(...)
            when(mock.fetchDependents("NPM", "left-pad", "1.2.3")).thenReturn(Optional.of(123));
            // stub fetchSourceRepoProjectKey(...)
            when(mock.fetchSourceRepoProjectKey("NPM", "left-pad", "1.2.3")).thenReturn(Optional.of("github.com/foo/left-pad"));
            // stub fetchScorecardAndStarsForksForProject(...)
            ComponentHealthMetaModel score = new ComponentHealthMetaModel(null);
            score.setStars(5);
            score.setForks(2);
            score.setOpenIssues(7);
            score.setScoreCardReferenceVersion("v2");
            score.setScoreCardTimestamp(Instant.parse("2025-07-10T00:00:00Z"));
            score.setScoreCardScore(9.1f);
            when(mock.fetchScorecardAndStarsForksForProject("github.com/foo/left-pad")).thenReturn(Optional.of(score));
        })) {

            // Stub out GitHubApiClient construction
            try (MockedConstruction<GitHubApiClient> ghMock = Mockito.mockConstruction(GitHubApiClient.class, (mock, ctx) -> {
                // tell it the connection was OK
                when(mock.connect()).thenReturn(true);
                // stub fetchDataFromGitHub(...)
                ComponentHealthMetaModel ghData = new ComponentHealthMetaModel(null);
                ghData.setContributors(42);
                ghData.setOpenPRs(3);
                ghData.setLastCommitDate(Instant.parse("2025-07-16T12:34:56Z"));
                ghData.setHasReadme(true);
                ghData.setHasCodeOfConduct(false);
                ghData.setHasSecurityPolicy(true);
                ghData.setFiles(100);
                ghData.setIsRepoArchived(false);
                ghData.setAvgIssueAgeDays(4);
                ghData.setBusFactor(2);
                ghData.setCommitFrequencyWeekly(12.5f);
                when(mock.fetchDataFromGitHub("github.com/foo/left-pad")).thenReturn(Optional.of(ghData));
            })) {

                // Build a PURL for NPM/left-pad (version here is ignored by analyze())
                PackageURL purl = new PackageURL("npm", null, "left-pad", "0.0.1", null, null);

                // Execute
                ComponentHealthMetaModel result = analyzer.analyze(purl);

                // Now verify that all pieces were merged in proper order:
                //  - Scorecard fields should be overridden by GitHub fields only if GitHub provided them
                //    (none overlap, so we expect to see both)
                assertEquals(5, result.getStars());
                assertEquals(2, result.getForks());
                assertEquals(7, result.getOpenIssues());
                assertEquals("v2", result.getScoreCardReferenceVersion());
                assertEquals(Instant.parse("2025-07-10T00:00:00Z"), result.getScoreCardTimestamp());
                assertEquals(9.1f, result.getScoreCardScore());

                // Dependents from deps.dev
                assertEquals(123, result.getDependents());

                // GitHub‐only fields
                assertEquals(42, result.getContributors());
                assertEquals(3, result.getOpenPRs());
                assertEquals(Instant.parse("2025-07-16T12:34:56Z"), result.getLastCommitDate());
                assertTrue(result.getHasReadme());
                assertFalse(result.getHasCodeOfConduct());
                assertTrue(result.getHasSecurityPolicy());
                assertEquals(100, result.getFiles());
                assertFalse(result.getIsRepoArchived());
                assertEquals(4, result.getAvgIssueAgeDays());
                assertEquals(2, result.getBusFactor());
                assertEquals(12.5f, result.getCommitFrequencyWeekly());
            }
        }
    }

    @Test
    void analyze_whenDepsDevReturnsNoVersion_earlyReturnsEmptyModel() throws Exception {
        // No GitHub config needed because we bail out first
        when(repoEntityRepository.findEnabledRepositoriesByType(any())).thenReturn(List.of());

        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            // simulate no version found
            when(mock.fetchLatestVersion("NPM", "left-pad")).thenReturn(Optional.empty());
        })) {

            PackageURL purl = new PackageURL("npm", null, "left-pad", "0.0.1", null, null);
            ComponentHealthMetaModel result = analyzer.analyze(purl);
            assertThat(result).hasAllNullFieldsOrPropertiesExcept("component");
        }
    }

    @Test
    void analyze_whenFetchDependentsEmpty_dependentsNullButContinues() throws Exception {
        // Prepare GitHub repo config
        Repository ghConfig = new Repository();
        ghConfig.setUrl(GITHUB_URL);
        when(repoEntityRepository.findEnabledRepositoriesByType(RepositoryType.GITHUB)).thenReturn(List.of(ghConfig));

        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            when(mock.fetchLatestVersion("NPM", "left-pad")).thenReturn(Optional.of("1.2.3"));
            when(mock.fetchDependents("NPM", "left-pad", "1.2.3")).thenReturn(Optional.empty());                  // no dependents
            when(mock.fetchSourceRepoProjectKey("NPM", "left-pad", "1.2.3")).thenReturn(Optional.of("github.com/foo/left-pad"));
            ComponentHealthMetaModel score = new ComponentHealthMetaModel(null);
            score.setStars(8);
            score.setForks(4);
            score.setOpenIssues(2);
            when(mock.fetchScorecardAndStarsForksForProject("github.com/foo/left-pad")).thenReturn(Optional.of(score));
        })) {
            try (MockedConstruction<GitHubApiClient> ghMock = Mockito.mockConstruction(GitHubApiClient.class, (mock, ctx) -> {
                when(mock.connect()).thenReturn(true);
                ComponentHealthMetaModel ghData = new ComponentHealthMetaModel(null);
                ghData.setContributors(11);
                when(mock.fetchDataFromGitHub("github.com/foo/left-pad")).thenReturn(Optional.of(ghData));
            })) {
                PackageURL purl = new PackageURL("npm", null, "left-pad", "0.0.1", null, null);
                ComponentHealthMetaModel result = analyzer.analyze(purl);

                // Dependents should remain null
                assertThat(result.getDependents()).isNull();
                // Scorecard data present
                assertThat(result.getStars()).isEqualTo(8);
                assertThat(result.getForks()).isEqualTo(4);
                assertThat(result.getOpenIssues()).isEqualTo(2);
                // GitHub data present
                assertThat(result.getContributors()).isEqualTo(11);
            }
        }
    }

    @Test
    void analyze_whenFetchSourceRepoProjectKeyEmpty_earlyReturnWithDependentsOnly() throws Exception {
        when(repoEntityRepository.findEnabledRepositoriesByType(any())).thenReturn(List.of()); // unused, we exit early

        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            when(mock.fetchLatestVersion("NPM", "left-pad")).thenReturn(Optional.of("1.2.3"));
            when(mock.fetchDependents("NPM", "left-pad", "1.2.3")).thenReturn(Optional.of(77));
            when(mock.fetchSourceRepoProjectKey("NPM", "left-pad", "1.2.3")).thenReturn(Optional.empty());                // no project key
        })) {
            PackageURL purl = new PackageURL("npm", null, "left-pad", "0.0.1", null, null);
            ComponentHealthMetaModel result = analyzer.analyze(purl);

            // Only dependents should be set
            assertThat(result.getDependents()).isEqualTo(77);
            assertThat(result).hasAllNullFieldsOrPropertiesExcept("component", "dependents");
        }
    }

    @Test
    void analyze_whenProjectIsNotGithub_returnsDepsDevAndScorecardOnly() throws Exception {
        when(repoEntityRepository.findEnabledRepositoriesByType(any())).thenReturn(List.of()); // unused, we exit on non-GitHub project

        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            when(mock.fetchLatestVersion("PYPI", "requests")).thenReturn(Optional.of("2.0.0"));
            when(mock.fetchDependents("PYPI", "requests", "2.0.0")).thenReturn(Optional.of(5));
            when(mock.fetchSourceRepoProjectKey("PYPI", "requests", "2.0.0")).thenReturn(Optional.of("gitlab.com/foo/requests"));
            ComponentHealthMetaModel score = new ComponentHealthMetaModel(null);
            score.setScoreCardScore(4.4f);
            when(mock.fetchScorecardAndStarsForksForProject("gitlab.com/foo/requests")).thenReturn(Optional.of(score));
        })) {
            PackageURL purl = new PackageURL("pypi", null, "requests", "2.0.0", null, null);
            ComponentHealthMetaModel result = analyzer.analyze(purl);

            // Dependents and scorecard only
            assertThat(result.getDependents()).isEqualTo(5);
            assertThat(result.getScoreCardScore()).isEqualTo(4.4f);
            // No GitHub fields
            assertThat(result).hasAllNullFieldsOrPropertiesExcept("component", "dependents", "scoreCardScore");
        }
    }

    @Test
    void analyze_whenGitHubConnectionFails_returnsDepsDevAndScorecardOnly() throws Exception {
        Repository ghConfig = new Repository();
        ghConfig.setUrl(GITHUB_URL);
        when(repoEntityRepository.findEnabledRepositoriesByType(RepositoryType.GITHUB)).thenReturn(List.of(ghConfig));

        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            when(mock.fetchLatestVersion("MAVEN", "commons-lang3")).thenReturn(Optional.of("3.12.0"));
            when(mock.fetchDependents("MAVEN", "commons-lang3", "3.12.0")).thenReturn(Optional.of(8));
            when(mock.fetchSourceRepoProjectKey("MAVEN", "commons-lang3", "3.12.0")).thenReturn(Optional.of("github.com/apache/commons-lang"));
            ComponentHealthMetaModel score = new ComponentHealthMetaModel(null);
            score.setScoreCardReferenceVersion("v3");
            when(mock.fetchScorecardAndStarsForksForProject("github.com/apache/commons-lang")).thenReturn(Optional.of(score));
        })) {
            try (MockedConstruction<GitHubApiClient> ghMock = Mockito.mockConstruction(GitHubApiClient.class, (mock, ctx) -> {
                when(mock.connect()).thenReturn(false);  // connection fails
            })) {
                PackageURL purl = new PackageURL("maven", "org.apache.commons", "commons-lang3", "3.12.0", null, null);
                ComponentHealthMetaModel result = analyzer.analyze(purl);

                // Scorecard and dependents present
                assertThat(result.getDependents()).isEqualTo(8);
                assertThat(result.getScoreCardReferenceVersion()).isEqualTo("v3");
                // No GitHub data
                assertThat(result.getContributors()).isNull();
                assertThat(result.getOpenPRs()).isNull();
            }
        }
    }

    @Test
    void analyze_whenScorecardEmpty_stillGetsGitHubData() throws Exception {
        Repository ghConfig = new Repository();
        ghConfig.setUrl(GITHUB_URL);
        when(repoEntityRepository.findEnabledRepositoriesByType(RepositoryType.GITHUB)).thenReturn(List.of(ghConfig));

        try (MockedConstruction<DepsDevApiClient> ddMock = Mockito.mockConstruction(DepsDevApiClient.class, (mock, ctx) -> {
            when(mock.fetchLatestVersion("GO", "gin")).thenReturn(Optional.of("v1.7.0"));
            when(mock.fetchDependents("GO", "gin", "v1.7.0")).thenReturn(Optional.of(15));
            when(mock.fetchSourceRepoProjectKey("GO", "gin", "v1.7.0")).thenReturn(Optional.of("github.com/gin-gonic/gin"));
            when(mock.fetchScorecardAndStarsForksForProject("github.com/gin-gonic/gin")).thenReturn(Optional.empty());  // no scorecard
        })) {
            try (MockedConstruction<GitHubApiClient> ghMock = Mockito.mockConstruction(GitHubApiClient.class, (mock, ctx) -> {
                when(mock.connect()).thenReturn(true);
                ComponentHealthMetaModel ghData = new ComponentHealthMetaModel(null);
                ghData.setContributors(99);
                when(mock.fetchDataFromGitHub("github.com/gin-gonic/gin")).thenReturn(Optional.of(ghData));
            })) {
                PackageURL purl = new PackageURL("golang", null, "gin", "v1.7.0", null, null);
                ComponentHealthMetaModel result = analyzer.analyze(purl);

                // No scorecard fields
                assertThat(result.getScoreCardScore()).isNull();
                assertThat(result.getScoreCardReferenceVersion()).isNull();
                // GitHub data still applied
                assertThat(result.getContributors()).isEqualTo(99);
            }
        }
    }
}