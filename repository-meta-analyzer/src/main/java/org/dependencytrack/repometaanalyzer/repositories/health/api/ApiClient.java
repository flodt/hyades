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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.kohsuke.github.GHFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

public abstract class ApiClient {
    @Inject
    @Named("httpClient")
    CloseableHttpClient httpClient;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final ObjectMapper mapper = new ObjectMapper();

    protected CloseableHttpResponse processHttpRequest(String url) throws IOException {
        final HttpUriRequest request = new HttpGet(url);
        request.addHeader("accept", "application/json");
        return httpClient.execute(request);
    }

    protected String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    protected <T> Optional<T> requestParseJsonForResult(String url, Function<JsonNode, Optional<T>> parser) {
        try (CloseableHttpResponse response = processHttpRequest(url)) {
            int status = response.getStatusLine().getStatusCode();
            if (status != HttpStatus.SC_OK) {
                this.logger.warn("API returned status {} for {}", status, url);
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.getEntity().getContent());
            return parser.apply(root);
        } catch (RuntimeException e) {
            this.logger.warn("Unexpected error during retrieval", e);
            return Optional.empty();
        } catch (IOException e) {
            this.logger.warn("I/O error during retrieval", e);
            return Optional.empty();
        }
    }

    protected <T> T safeFetchValue(ApiClient.ThrowingAPICall<T> call, T defaultValue) {
        try {
            return call.call();
        } catch (GHFileNotFoundException e) {
            // this is an expected exception indicating that a file does not exist in the GitHub repo, and is used
            //      while checking for README.md, etc. in GitHubApiClient
            return defaultValue;
        } catch (IOException e) {
            logger.warn("I/O error during API call", e);
            return defaultValue;
        } catch (InterruptedException e) {
            logger.warn("API call was interrupted", e);
            return defaultValue;
        }
    }

    @FunctionalInterface
    public interface ThrowingAPICall<T> {
        T call() throws IOException, InterruptedException;
    }
}
