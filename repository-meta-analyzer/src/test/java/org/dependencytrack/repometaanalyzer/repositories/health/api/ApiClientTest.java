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
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApiClientTest {
    // A concrete subclass so we can instantiate ApiClient
    static class DummyApiClient extends ApiClient {}

    private DummyApiClient apiClient;
    private CloseableHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(CloseableHttpClient.class);
        apiClient = new DummyApiClient();
        apiClient.httpClient = mockHttpClient;
    }

    @Test
    void urlEncode_encodesSpacesAndUnicode() {
        String original = "a b/ä+";
        // we expect spaces → %20, slash and plus are encoded by URLEncoder but slash remains slash
        String encoded = apiClient.urlEncode(original);
        // compare to manual URLEncoder behavior with space fix
        String expected = URLEncoder.encode(original, StandardCharsets.UTF_8).replace("+", "%20");
        assertThat(encoded).isEqualTo(expected);
    }

    @Test
    void processHttpRequest_invokesHttpClientWithGetAndAcceptHeader() throws Exception {
        CloseableHttpResponse fakeResponse = mock(CloseableHttpResponse.class);
        when(mockHttpClient.execute(any(HttpUriRequest.class))).thenReturn(fakeResponse);

        CloseableHttpResponse returned = apiClient.processHttpRequest("http://example.com/test?x=1");

        // verify the client was called exactly once
        ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(mockHttpClient, times(1)).execute(captor.capture());
        HttpUriRequest req = captor.getValue();

        assertThat(req.getURI().toString()).isEqualTo("http://example.com/test?x=1");
        assertThat(req.getFirstHeader("accept").getValue()).isEqualTo("application/json");
        assertThat(returned).isSameAs(fakeResponse);
    }

    @Test
    void requestParseJsonForResult_successful200AndParse() throws Exception {
        // prepare a JSON body: { "foo": "bar" }
        String json = "{\"foo\":\"bar\"}";
        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(stream);
        when(mockHttpClient.execute(any())).thenReturn(response);

        Optional<String> result = apiClient.requestParseJsonForResult(
                "http://irrelevant",
                root -> Optional.of(root.get("foo").asText())
        );

        assertThat(result).contains("bar");
        // response should be closed by the method
        verify(response, times(1)).close();
    }

    @Test
    void requestParseJsonForResult_non200Status_returnsEmpty() throws Exception {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(404);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(mockHttpClient.execute(any())).thenReturn(response);

        Optional<Object> result = apiClient.requestParseJsonForResult(
                "http://irrelevant",
                root -> Optional.of("won't be used")
        );

        assertThat(result).isEmpty();
        verify(response, times(1)).close();
    }

    @Test
    void requestParseJsonForResult_ioErrorReadingJson_returnsEmpty() throws Exception {
        // simulate a stream that throws on read
        InputStream badStream = mock(InputStream.class);
        when(badStream.read(any())).thenThrow(new IOException("broken"));

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(badStream);
        when(mockHttpClient.execute(any())).thenReturn(response);

        Optional<Integer> result = apiClient.requestParseJsonForResult(
                "http://irrelevant",
                root -> Optional.of(1)
        );

        assertThat(result).isEmpty();
        verify(response, times(1)).close();
    }

    @Test
    void requestParseJsonForResult_parserThrowsRuntimeException_returnsEmpty() throws Exception {
        String json = "{\"dummy\":123}";
        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(stream);
        when(mockHttpClient.execute(any())).thenReturn(response);

        Optional<String> result = apiClient.requestParseJsonForResult(
                "http://irrelevant",
                root -> { throw new RuntimeException("oops"); }
        );

        assertThat(result).isEmpty();
        verify(response, times(1)).close();
    }

    @Test
    void safeFetchValue_returnsValueOrDefaultOnException() {
        String val = apiClient.safeFetchValue(() -> "hello", "def");
        assertThat(val).isEqualTo("hello");

        String ioDefault = apiClient.safeFetchValue(() -> { throw new IOException("fail"); }, "d1");
        assertThat(ioDefault).isEqualTo("d1");

        String interruptedDefault = apiClient.safeFetchValue(() -> { throw new InterruptedException(); }, "d2");
        assertThat(interruptedDefault).isEqualTo("d2");
    }
}
