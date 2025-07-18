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

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.dependencytrack.common.SecretDecryptor;
import org.dependencytrack.persistence.model.Repository;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@Ignore
class GitHubApiClientExternalIntegrationTest {
    @Inject
    GitHubApiClient gitHubApiClient;

    @InjectMock
    SecretDecryptor secretDecryptor;

    @Test
    void connectedWithNoCredentials() {
        Repository credentials = new Repository();
        credentials.setPassword(null);

        assertThat(this.gitHubApiClient.connect(credentials)).isFalse();
        assertThat(this.gitHubApiClient.fetchDataFromGitHub("project")).isEmpty();
    }

    @Test
    void connectedWithCredentials() throws Exception {
        Repository credentials = new Repository();

        // these are obviously not valid credentials, could be pulled in through env variable if desired
        credentials.setUsername("testuser");
        credentials.setPassword("PLACEHOLDER");
        when(secretDecryptor.decryptAsString(any())).thenReturn("testtoken");

        assertThat(this.gitHubApiClient.connect(credentials)).isTrue();
        assertThat(this.gitHubApiClient.fetchDataFromGitHub("github.com/pandas-dev/pandas")).isPresent();
    }
}