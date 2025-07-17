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

import org.dependencytrack.common.SecretDecryptor;
import org.dependencytrack.persistence.model.Repository;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.util.GitHubUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryStatistics;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class GitHubApiClientTest {

    private GitHubApiClient client;
    private Repository credentials;
    private SecretDecryptor mockDecryptor;

    @BeforeEach
    void setUp() {
        // Prepare repo credentials
        credentials = new Repository();
        credentials.setUsername("user");
        credentials.setPassword("enc‐pw");

        // Spy/delegate decryptor
        mockDecryptor = mock(SecretDecryptor.class);

        // Create client and inject decryptor
        client = new GitHubApiClient();
        client.secretDecryptor = mockDecryptor;
    }

    @Test
    void connect_failsOnDecrypt_andDidConnectionFail() throws Exception {
        // Decrypt throws
        when(mockDecryptor.decryptAsString("enc‐pw")).thenThrow(new Exception("bad"));
        assertThat(client.connect(credentials)).isFalse();
    }

    @Test
    void connect_failsOnIOException_andDidConnectionFail() throws Exception {
        when(mockDecryptor.decryptAsString("enc‐pw")).thenReturn("token");
        try (var ghUtil = mockStatic(GitHubUtil.class)) {
            ghUtil.when(() -> GitHubUtil.connectToGitHub(any(), any(), any()))
                    .thenThrow(new IOException("net"));
            assertThat(client.connect(credentials)).isFalse();
        }
    }

    @Test
    void fetchDataFromGitHub_notConnected_returnsEmpty() {
        // never called connect()
        assertThat(client.fetchDataFromGitHub("github.com/foo/bar")).isEmpty();
    }

    @Test
    void fetchDataFromGitHub_happyPath_populatesAllFields() throws Exception {
        // 1) connect
        when(mockDecryptor.decryptAsString("enc‐pw")).thenReturn("token");
        GitHub fakeGh = mock(GitHub.class);
        try (var ghUtil = mockStatic(GitHubUtil.class)) {
            ghUtil.when(() -> GitHubUtil.connectToGitHub("user", "token", GitHubApiClient.GITHUB_URL))
                    .thenReturn(fakeGh);
            assertThat(client.connect(credentials)).isTrue();

            // 2) GHRepository stub
            GHRepository repo = mock(GHRepository.class);
            when(fakeGh.getRepository("owner/rep")).thenReturn(repo);

            // contributors → 3
            PagedIterable users = mock(PagedIterable.class);
            when(repo.listContributors()).thenReturn(users);
            when(users.toList()).thenReturn(List.of(mock(GHUser.class), mock(GHUser.class), mock(GHUser.class)));

            // open PRs → 2
            when(repo.getPullRequests(GHIssueState.OPEN))
                    .thenReturn(List.of(mock(GHPullRequest.class), mock(GHPullRequest.class)));

            // last commit → Instant.now() − 2 days
            GHBranch branch = mock(GHBranch.class);
            when(repo.getDefaultBranch()).thenReturn("main");
            when(repo.getBranch("main")).thenReturn(branch);
            when(branch.getSHA1()).thenReturn("abc123");
            GHCommit commit = mock(GHCommit.class);
            Instant twoDaysAgo = Instant
                    .now()
                    .minus(2, ChronoUnit.DAYS)
                    .truncatedTo(ChronoUnit.MILLIS);
            when(commit.getCommitDate()).thenReturn(Date.from(twoDaysAgo));
            when(repo.getCommit("abc123")).thenReturn(commit);

            // hasReadme / CoC / Security
            when(repo.getReadme()).thenReturn(mock(GHContent.class));
            when(repo.getFileContent("CODE_OF_CONDUCT.md")).thenReturn(mock(GHContent.class));
            when(repo.getFileContent(".github/SECURITY.md")).thenReturn(mock(GHContent.class));

            // files → 2 blobs, 1 tree
            GHTreeEntry blob1 = mock(GHTreeEntry.class), blob2 = mock(GHTreeEntry.class),
                    treeE = mock(GHTreeEntry.class);
            when(blob1.getType()).thenReturn("blob");
            when(blob2.getType()).thenReturn("blob");
            when(treeE.getType()).thenReturn("tree");
            GHTree ghTree = mock(GHTree.class);
            when(repo.getTree("abc123")).thenReturn(ghTree);
            when(ghTree.getTree()).thenReturn(List.of(blob1, blob2, treeE));

            // archived
            when(repo.isArchived()).thenReturn(true);

            // open issues → empty ⇒ avgAge = 0
            when(repo.getIssues(GHIssueState.OPEN)).thenReturn(Collections.emptyList());

            // stats → two contributors: 10 and 20 commits, created 4 weeks ago
            GHRepositoryStatistics stats = mock(GHRepositoryStatistics.class);
            GHRepositoryStatistics.ContributorStats cs1 = mock(GHRepositoryStatistics.ContributorStats.class);
            GHRepositoryStatistics.ContributorStats cs2 = mock(GHRepositoryStatistics.ContributorStats.class);
            GHRepositoryStatistics.ContributorStats cs3 = mock(GHRepositoryStatistics.ContributorStats.class);
            when(cs1.getTotal()).thenReturn(8);
            when(cs2.getTotal()).thenReturn(5);
            when(cs3.getTotal()).thenReturn(7);
            PagedIterable<GHRepositoryStatistics.ContributorStats> statsPaged = mock(PagedIterable.class);
            when(statsPaged.toList()).thenReturn(List.of(cs1, cs2, cs3));
            when(stats.getContributorStats()).thenReturn(statsPaged);
            when(repo.getStatistics()).thenReturn(stats);
            // created 4 weeks ago → commitFrequencyWeekly = (8+5+7)/4 = 5
            Date fakeDate = Date.from(Instant.now().minus(4 * 7, ChronoUnit.DAYS));
            doReturn(fakeDate)
                    .when(repo)
                    .getCreatedAt();

            // Execute
            Optional<ComponentHealthMetaModel> opt = client.fetchDataFromGitHub("github.com/owner/rep");
            assertThat(opt).isPresent();
            ComponentHealthMetaModel m = opt.get();

            assertThat(m.getContributors()).isEqualTo(3);
            assertThat(m.getOpenPRs()).isEqualTo(2);
            assertThat(m.getLastCommitDate()).isEqualTo(twoDaysAgo);
            assertThat(m.getHasReadme()).isTrue();
            assertThat(m.getHasCodeOfConduct()).isTrue();
            assertThat(m.getHasSecurityPolicy()).isTrue();
            assertThat(m.getFiles()).isEqualTo(2);
            assertThat(m.getIsRepoArchived()).isTrue();
            assertThat(m.getAvgIssueAgeDays()).isEqualTo(0);
            assertThat(m.getCommitFrequencyWeekly()).isEqualTo(5.0f);
            // busFactor: total=20, half=10, sorted [8,7,5]: takeWhile <10 → only 8 (+ 1 to get over) = 2
            assertThat(m.getBusFactor()).isEqualTo(2);
        }
    }

    @Test
    void fetchDataFromGitHub_safeFetchFailures_defaultsApplied() throws Exception {
        // connect
        when(mockDecryptor.decryptAsString("enc‐pw")).thenReturn("token");
        GitHub fakeGh = mock(GitHub.class);
        try (var ghUtil = mockStatic(GitHubUtil.class)) {
            ghUtil.when(() -> GitHubUtil.connectToGitHub(any(), any(), any()))
                    .thenReturn(fakeGh);
            assertThat(client.connect(credentials)).isTrue();

            // repo stub
            GHRepository repo = mock(GHRepository.class);
            when(fakeGh.getRepository(any())).thenReturn(repo);

            // contributors throws → null
            when(repo.listContributors()).thenThrow(new IOException());
            // openPRs throws → null
            when(repo.getPullRequests(any())).thenThrow(new IOException());
            // lastCommit throws → null
            when(repo.getBranch(any())).thenThrow(new IOException());
            // readme throws → hasReadme=false
            when(repo.getReadme()).thenThrow(new IOException());
            // CoC throws → hasCodeOfConduct=false
            when(repo.getFileContent("CODE_OF_CONDUCT.md")).thenThrow(new IOException());
            // security throws → hasSecurityPolicy=false
            when(repo.getFileContent(".github/SECURITY.md")).thenThrow(new IOException());
            // tree throws → files=null
            when(repo.getTree(any())).thenThrow(new IOException());
            // archived: return false
            when(repo.isArchived()).thenReturn(false);
            // issues empty → avgIssueAgeDays=0
            when(repo.getIssues(any())).thenReturn(Collections.emptyList());
            // stats empty → commitFrequencyWeekly=null & busFactor null
            GHRepositoryStatistics stats = mock(GHRepositoryStatistics.class);
            PagedIterable<GHRepositoryStatistics.ContributorStats> emptyPaged = mock(PagedIterable.class);
            when(emptyPaged.toList()).thenReturn(Collections.emptyList());
            when(stats.getContributorStats()).thenReturn(emptyPaged);
            when(repo.getStatistics()).thenReturn(stats);

            Optional<ComponentHealthMetaModel> opt = client.fetchDataFromGitHub("github.com/x/y");
            assertThat(opt).isPresent();
            ComponentHealthMetaModel m = opt.get();

            assertThat(m.getContributors()).isNull();
            assertThat(m.getOpenPRs()).isNull();
            assertThat(m.getLastCommitDate()).isNull();
            assertThat(m.getHasReadme()).isFalse();
            assertThat(m.getHasCodeOfConduct()).isFalse();
            assertThat(m.getHasSecurityPolicy()).isFalse();
            assertThat(m.getFiles()).isNull();
            assertThat(m.getIsRepoArchived()).isFalse();
            assertThat(m.getAvgIssueAgeDays()).isEqualTo(0);
            assertThat(m.getCommitFrequencyWeekly()).isNull();
            assertThat(m.getBusFactor()).isNull();
        }
    }
}
