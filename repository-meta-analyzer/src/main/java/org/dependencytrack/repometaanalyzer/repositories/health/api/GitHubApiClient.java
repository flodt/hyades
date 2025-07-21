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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dependencytrack.common.SecretDecryptor;
import org.dependencytrack.persistence.model.Repository;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.util.GitHubUtil;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryStatistics;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.lang3.StringUtils.isBlank;

@ApplicationScoped
public class GitHubApiClient extends ApiClient {
    public static final String GITHUB_URL = "https://github.com";

    private static final int CONTRIBUTOR_STATS_RETRY_COUNT = 3;
    private static final int CONTRIBUTOR_STATS_WAIT_TIME_MILLIS = 20_000;

    @Inject
    SecretDecryptor secretDecryptor;

    private GitHub gitHub;

    public boolean connect(Repository credentials) {
        try {
            String user = credentials.getUsername();
            String password = credentials.getPassword();

            // we need to be authenticated for our purposes
            if (isBlank(password)) {
                logger.info("No credential found for GitHub, can not use authenticated API functions");
                return false;
            }

            password = secretDecryptor.decryptAsString(password);

            this.gitHub = GitHubUtil.connectToGitHub(user, password, GITHUB_URL);
            return true;
        } catch (IOException e) {
            logger.warn("Failed to connect to GitHub", e);
        } catch (Exception e) {
            logger.warn("Credentials retrieval failed", e);
        }

        return false;
    }

    public Optional<ComponentHealthMetaModel> fetchDataFromGitHub(String project) {
        if (this.gitHub == null) {
            logger.warn("GitHub client is not connected - did you forget to call .connect()?");
            return Optional.empty();
        }

        try {
            ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(null);
            GHRepository repository = gitHub.getRepository(project.replace("github.com/", ""));

            metaModel.setContributors(safeFetchValue(() -> repository.listContributors().toList().size(), null));
            metaModel.setOpenPRs(safeFetchValue(() -> repository.getPullRequests(GHIssueState.OPEN).size(), null));
            metaModel.setLastCommitDate(safeFetchValue(() -> {
                String head = repository.getBranch(repository.getDefaultBranch()).getSHA1();
                return repository.getCommit(head).getCommitDate().toInstant();
            }, null));

            metaModel.setHasReadme(safeFetchValue(() -> {
                repository.getReadme();
                // if we didn't have a readme, we would have thrown here
                return true;
            }, false));

            metaModel.setHasCodeOfConduct(safeFetchValue(() -> {
                repository.getFileContent("CODE_OF_CONDUCT.md");
                // see above
                return true;
            }, false));

            metaModel.setHasSecurityPolicy(safeFetchValue(() -> {
                repository.getFileContent(".github/SECURITY.md");
                // see above
                return true;
            }, false));

            metaModel.setFiles(
                    safeFetchValue(() ->
                                    (int) repository
                                            .getTreeRecursive(
                                                    repository.getBranch(repository.getDefaultBranch()).getSHA1(),
                                                    1
                                            )
                                            .getTree()
                                            .stream()
                                            .filter(Objects::nonNull)
                                            .filter(entry -> "blob".equals(entry.getType()))
                                            .count(),
                            null)
            );

            metaModel.setIsRepoArchived(repository.isArchived());

            metaModel.setAvgIssueAgeDays(
                    safeFetchValue(() -> {
                                List<GHIssue> issues = repository.getIssues(GHIssueState.OPEN);

                                if (issues.isEmpty()) {
                                    return 0;
                                }

                                long sumDays = 0;
                                Instant now = Instant.now();

                                for (GHIssue issue : issues) {
                                    Instant created = issue.getCreatedAt().toInstant();
                                    long age = Duration.between(created, now).toDays();
                                    sumDays += age;
                                }

                                double avg = (double) sumDays / (double) issues.size();
                                return (int) Math.round(avg);
                            },
                            null)
            );

            // fetch contributor stats, we'll need them for the remaining checks
            List<GHRepositoryStatistics.ContributorStats> stats = safeFetchValue(() -> {
                /*
                    The waitTillReady parameter in getContributorStats() has no effect, as getContributorStatsImpl()
                    always returns a non-null PagedIterable.
                    see also: https://github.com/hub4j/github-api/issues/1540
                    Therefore, the polling loop is never triggered, and the internal fetching of the next page done in
                    PagedIterable.toList() then blows up because base.next() is null.
                    We work around this by catching this NPE and trying again with the specified configuration.
                 */

                for (int i = 0; i < CONTRIBUTOR_STATS_RETRY_COUNT; i++) {
                    try {
                        return repository
                                .getStatistics()
                                .getContributorStats(true)
                                .toList();
                    } catch (NullPointerException e) {
                        // the stats are not present yet, we try again
                        Thread.sleep(CONTRIBUTOR_STATS_WAIT_TIME_MILLIS);
                    }
                }

                throw new IOException(
                        "Failed to retrieve contributor stats after " + CONTRIBUTOR_STATS_RETRY_COUNT + " retries"
                );
            }, List.of());

            metaModel.setCommitFrequencyWeekly(safeFetchValue(() -> {
                if (stats.isEmpty()) {
                    throw new IOException("No contributor stats found, can't compute weekly commit frequency");
                }

                int totalCommits = stats
                        .stream()
                        .mapToInt(GHRepositoryStatistics.ContributorStats::getTotal)
                        .sum();

                // needs static helper method for testability - see GitHubApiClientTest
                Instant created = GitHubUtil.getRepositoryCreatedAt(repository).toInstant();
                long days = ChronoUnit.DAYS.between(created, Instant.now());
                long weeks = days / 7;
                if (weeks <= 0) {
                    // brand‑new repo: count all commits as one week
                    return (float) totalCommits;
                }

                return (float) totalCommits / (float) weeks;
            }, null));

            metaModel.setBusFactor(safeFetchValue(() -> {
                if (stats.isEmpty()) {
                    throw new IOException("No contributor stats found, can't compute bus factor");
                }

                int totalCommits = stats.stream()
                        .mapToInt(GHRepositoryStatistics.ContributorStats::getTotal)
                        .sum();
                int halfCommits = totalCommits / 2;
                AtomicInteger currentCommits = new AtomicInteger(0);

                // the stream-expression is off-by-one because takeWhile stops 'before' the predicate is flipped
                return 1 + (int) stats
                        .stream()
                        .map(GHRepositoryStatistics.ContributorStats::getTotal)
                        .sorted(Comparator.reverseOrder())
                        .map(currentCommits::addAndGet)
                        .takeWhile(c -> c < halfCommits)
                        .count();
            }, null));

            return Optional.of(metaModel);
        } catch (IOException e) {
            logger.warn("GitHub repository retrieval failed", e);
            return Optional.empty();
        }
    }
}
