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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DepsDevHealthMetaSourceCodeMapper implements IHealthMetaSourceCodeMapper {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    DepsDevApiClient depsDevApiClient;

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
    public Optional<String> findSourceCodeFor(PackageURL packageURL) {
        String system = Optional
                .ofNullable(SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM.get(packageURL.getType()))
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported PURL type: " + packageURL.getType()));
        String name = Optional.ofNullable(packageURL.getNamespace())
                .filter(ns -> !ns.isEmpty())
                .map(ns -> ns + ":")
                .orElse("")
                + packageURL.getName();

        // collect latest version
        Optional<String> maybeLatestVersion = depsDevApiClient.fetchLatestVersion(system, name);
        if (maybeLatestVersion.isEmpty()) {
            logger.warn("Could not determine latest version on deps.dev for {}", packageURL);
            return Optional.empty();
        }
        String latestVersion = maybeLatestVersion.get();

        // collect package information on the latest version
        Optional<String> maybeProject = depsDevApiClient.fetchSourceRepoProjectKey(system, name, latestVersion);
        if (maybeProject.isEmpty()) {
            logger.info("Could not determine source code project for {}", packageURL);
        }

        return maybeProject;
    }
}
