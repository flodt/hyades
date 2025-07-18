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

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.dependencytrack.persistence.model.Repository;
import org.dependencytrack.persistence.model.RepositoryType;
import org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient;
import org.dependencytrack.repometaanalyzer.repositories.health.api.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class DepsDevGitHubHealthMetaAnalyzerPersistenceTest {
    @Inject
    DepsDevGitHubHealthMetaAnalyzer analyzer;

    @Inject
    EntityManager entityManager;

    @InjectMock
    DepsDevApiClient depsDevApiClient;

    @InjectMock
    GitHubApiClient gitHubApiClient;

    @BeforeEach
    void beforeEach() {
        when(depsDevApiClient.fetchLatestVersion(any(), any())).thenReturn(Optional.of("1.2.3"));
        when(depsDevApiClient.fetchSourceRepoProjectKey(any(), any(), any()))
                .thenReturn(Optional.of("github.com/example/foo"));
        when(gitHubApiClient.connect(any())).thenReturn(false);
    }

    @Test
    @TestTransaction
    void testGetsRepositoryData() throws MalformedPackageURLException {
        Repository repo = new Repository();
        repo.setEnabled(true);
        repo.setIdentifier("github");
        repo.setUrl("https://github.com");
        repo.setUsername("username");
        repo.setPassword("password");
        repo.setResolutionOrder(2);
        repo.setType(RepositoryType.GITHUB);
        entityManager.persist(repo);
        entityManager.flush();

        PackageURL purl = new PackageURL("pkg:maven/com.example/foo@1.2.3");
        analyzer.analyze(purl);

        verify(gitHubApiClient).connect(
                argThat(
                        cfg ->
                                cfg.getId() == repo.getId()
                                        && cfg.isEnabled()
                                        && "github".equals(cfg.getIdentifier())
                                        && "https://github.com".equals(cfg.getUrl())
                                        && "username".equals(cfg.getUsername())
                                        && "password".equals(cfg.getPassword())
                                        && cfg.getResolutionOrder() == 2
                                        && cfg.getType() == RepositoryType.GITHUB
                )
        );
    }
}