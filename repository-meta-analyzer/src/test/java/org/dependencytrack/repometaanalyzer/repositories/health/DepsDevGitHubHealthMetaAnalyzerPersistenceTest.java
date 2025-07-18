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

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.dependencytrack.persistence.model.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class DepsDevGitHubHealthMetaAnalyzerPersistenceTest {
    @Inject
    EntityManager entityManager;

    @Inject
    DepsDevGitHubHealthMetaAnalyzer analyzer;

    @BeforeEach
    void beforeEach() {

    }

    @Test
    @TestTransaction
    void testGetsRepositoryData() {
        entityManager.createNativeQuery("""
                INSERT INTO "REPOSITORY" ("ID", "ENABLED", "IDENTIFIER", "INTERNAL", "PASSWORD", "RESOLUTION_ORDER", "TYPE", "URL", "USERNAME") VALUES
                                    (1, 'true', 'github', 'false', 'password', 2, 'GITHUB', 'https://github.com', 'username');
                """).executeUpdate();

        Optional<Repository> maybeRepository = analyzer.retrieveGitHubRepositoryConfig();
        assertThat(maybeRepository).isNotEmpty();

        Repository repository = maybeRepository.get();
        assertThat(repository.getId()).isEqualTo(1);
        assertThat(repository.isEnabled()).isTrue();
        assertThat(repository.getIdentifier()).isEqualTo("github");
        assertThat(repository.getPassword()).isEqualTo("password");
        assertThat(repository.getResolutionOrder()).isEqualTo(2);
        assertThat(repository.getType()).asString().isEqualTo("GITHUB");
        assertThat(repository.getUrl()).isEqualTo("https://github.com");
        assertThat(repository.getUsername()).isEqualTo("username");
    }
}