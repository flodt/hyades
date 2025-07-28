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

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DepsDevApiClientTest {
    private DepsDevApiClient client;
    private CloseableHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(CloseableHttpClient.class);
        client = new DepsDevApiClient();
        client.httpClient = mockHttpClient;
    }

    private CloseableHttpResponse mockResponse(int status, String jsonBody) throws Exception {
        CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);
        InputStream content = new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8));

        when(statusLine.getStatusCode()).thenReturn(status);
        when(resp.getStatusLine()).thenReturn(statusLine);
        when(resp.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(content);
        return resp;
    }

    @Test
    void fetchLatestVersion_returnsDefaultVersion() throws Exception {
        String json = """
                {
                  "versions": [
                    {"isDefault": false, "versionKey": {"version":"9.9.9"}},
                    {"isDefault": true,  "versionKey": {"version":"1.2.3"}}
                  ]
                }
                """;
        CloseableHttpResponse fake = mockResponse(200, json);
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<String> v = client.fetchLatestVersion("MAVEN", "org.foo:bar");
        assertThat(v).contains("1.2.3");
    }

    @Test
    void fetchLatestVersion_noDefault_returnsEmpty() throws Exception {
        String json = """
                { "versions": [
                    {"isDefault": false, "versionKey": {"version":"x"}}
                  ]}
                """;
        CloseableHttpResponse fake = mockResponse(200, json);
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<String> v = client.fetchLatestVersion("MAVEN", "org.foo:bar");
        assertThat(v).isEmpty();
    }

    @Test
    void fetchDependents_parsesCount() throws Exception {
        String json = "{ \"dependentCount\": 42 }";
        CloseableHttpResponse fake = mockResponse(200, json);
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<Integer> count = client.fetchDependents("PYPI", "requests", "2.25.1");
        assertThat(count).contains(42);
    }

    @Test
    void fetchSourceRepoProjectKey_picksSourceRepo() throws Exception {
        String json = """
                {
                  "relatedProjects": [
                    {"relationType":"OTHER",      "projectKey":{"id":"x"}},
                    {"relationType":"SOURCE_REPO","projectKey":{"id":"github.com/foo/bar"}}
                  ]
                }
                """;
        CloseableHttpResponse fake = mockResponse(200, json);
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<String> key = client.fetchSourceRepoProjectKey("NPM", "left-pad", "1.3.0");
        assertThat(key).contains("github.com/foo/bar");
    }

    @Test
    void fetchSourceRepoProjectKey_none_returnsEmpty() throws Exception {
        String json = "{ \"relatedProjects\": [] }";
        CloseableHttpResponse fake = mockResponse(200, json);
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<String> key = client.fetchSourceRepoProjectKey("NPM", "left-pad", "1.3.0");
        assertThat(key).isEmpty();
    }

    @Test
    void fetchScorecardAndStarsForksIssuesForProject_parsesAllFields() throws Exception {
        String json = """
                {
                  "openIssuesCount": 5,
                  "starsCount": 10,
                  "forksCount": 3,
                  "scorecard": {
                    "scorecard": { "version": "v1" },
                    "date": "2025-07-15T12:00:00Z",
                    "overallScore": 8.5,
                    "checks": [
                      {
                        "name": "CHECK1",
                        "score": 1.0,
                        "documentation": {
                          "shortDescription": "Desc",
                          "url": "http://doc"
                        },
                        "reason": "Good",
                        "details": ["d1","d2"]
                      }
                    ]
                  }
                }
                """;
        CloseableHttpResponse fake = mockResponse(200, json);
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<ComponentHealthMetaModel> opt = client.fetchScorecardAndStarsForksIssuesForProject("github.com/foo/bar");
        assertThat(opt).isPresent();
        ComponentHealthMetaModel m = opt.get();

        assertThat(m.getOpenIssues()).isEqualTo(5);
        assertThat(m.getStars()).isEqualTo(10);
        assertThat(m.getForks()).isEqualTo(3);
        assertThat(m.getScoreCardReferenceVersion()).isEqualTo("v1");
        assertThat(m.getScoreCardTimestamp()).isEqualTo(Instant.parse("2025-07-15T12:00:00Z"));
        assertThat(m.getScoreCardScore()).isEqualTo(8.5f);

        assertThat(m.getScoreCardChecks()).hasSize(1).first().satisfies(check -> {
            assertThat(check.getName()).isEqualTo("CHECK1");
            assertThat(check.getScore()).isEqualTo(1.0f);
            assertThat(check.getDescription()).isEqualTo("Desc");
            assertThat(check.getDocumentationUrl()).isEqualTo("http://doc");
            assertThat(check.getReason()).isEqualTo("Good");
            assertThat(check.getDetails()).containsExactly("d1", "d2");
        });
    }

    @Test
    void fetchLatestVersion_non200_returnsEmpty() throws Exception {
        CloseableHttpResponse fake = mockResponse(500, "{}");
        when(mockHttpClient.execute(any())).thenReturn(fake);

        Optional<String> v = client.fetchLatestVersion("MAVEN", "anything");
        assertThat(v).isEmpty();
    }
}
