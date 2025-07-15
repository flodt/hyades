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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.packageurl.PackageURL;
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class DepsDevGitHubHealthMetaAnalyzer extends AbstractHealthMetaAnalyzer {
    private static final Map<String, String> SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM = Map.ofEntries(
            Map.entry(PackageURL.StandardTypes.NPM, "NPM"),
            Map.entry(PackageURL.StandardTypes.GOLANG, "GO"),
            Map.entry(PackageURL.StandardTypes.MAVEN, "MAVEN"),
            Map.entry(PackageURL.StandardTypes.PYPI, "PYPI"),
            Map.entry(PackageURL.StandardTypes.NUGET, "NUGET"),
            Map.entry(PackageURL.StandardTypes.CARGO, "CARGO"),
            Map.entry(PackageURL.StandardTypes.GEM, "RUBYGEMS")
    );

    @Override
    public boolean isApplicable(PackageURL packageURL) {
        return SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM.containsKey(packageURL.getType());
    }

    @Override
    public ComponentHealthMetaModel analyze(PackageURL packageURL) {
        Component component = new Component();
        component.setPurl(packageURL);
        ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(component);

        /*
         * We fetch the desired health metadata through a combination of deps.dev and the GitHub APIs.
         * We first get the package information from deps.dev, which contains a set of versions.
         * There, we pick the latest one and extract the source code repository from the information retrieved through
         * the GetVersion endpoint.
         * We can then get the corresponding project information including the OpenSSF Scorecard data from the project
         * endpoint, after which we can fill in the rest of the metadata from the GitHub API.
         * If the project is not hosted on GitHub, there is currently no way to fetch its metadata beyond what we get
         * from deps.dev.
         */

        String system = Optional
                .ofNullable(SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM.get(packageURL.getType()))
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported PURL type: " + packageURL.getType()));
        String name = packageURL.getName();

        // collect latest version
        Optional<String> maybeVersion = fetchLatestVersion(system, name);
        if (maybeVersion.isEmpty()) {
            logger.warn("Could not determine latest version for {}", packageURL);
            return metaModel;
        }
        String version = maybeVersion.get();

        // collect package information on this candidate version
        Optional<String> maybeProject = fetchSourceRepoProjectKey(system, name, version);
        if (maybeProject.isEmpty()) {
            logger.warn("Could not determine source code project for {}", packageURL);
            return metaModel;
        }
        String project = maybeProject.get();

        // todo continue here

        return metaModel;
    }

    private Optional<String> fetchLatestVersion(String system, String name) {
        String url = "https://api.deps.dev/v3/systems" + system + "/packages/" + name;
        return requestParseJsonForResult(url, (root) -> {
            JsonNode versionsNode = root.get("versions");
            return StreamSupport
                    .stream(versionsNode.spliterator(), false)
                    .filter(n -> n.path("isDefault").asBoolean(false))
                    .map(n -> n.path("versionKey").path("version").asText(null))
                    .filter(Objects::nonNull)
                    .findFirst();
        });
    }

    private Optional<String> fetchSourceRepoProjectKey(String system, String name, String version) {
        String url = "https://api.deps.dev/v3/systems" + system + "/packages/" + name + "/versions/" + version;
        return requestParseJsonForResult(url, (root) -> {
            // todo continue and extract project here
            return Optional.empty();
        });
    }

}
