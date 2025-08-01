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
package org.dependencytrack.repometaanalyzer.repositories.health.packageanalyzer;

import com.github.packageurl.PackageURL;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.repositories.health.HealthMetaModelFactory;
import org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient.SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM;

@ApplicationScoped
public class DepsDevHealthMetaPackageAnalyzer implements IHealthMetaPackageAnalyzer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    DepsDevApiClient depsDevApiClient;

    @Override
    public boolean isApplicable(PackageURL packageURL) {
        return SUPPORTED_PURL_TYPE_TO_DEPS_DEV_SYSTEM.containsKey(packageURL.getType());
    }

    @Override
    public ComponentHealthMetaModel analyze(PackageURL packageURL) {
        ComponentHealthMetaModel metaModel = HealthMetaModelFactory.create(packageURL);

        String system = DepsDevApiClient.getSystemForPurl(packageURL);
        String name = DepsDevApiClient.getNamespacedNameForPurl(packageURL);

        // collect latest version
        Optional<String> maybeLatestVersion = depsDevApiClient.fetchLatestVersion(system, name);
        if (maybeLatestVersion.isEmpty()) {
            logger.warn("Could not determine latest version on deps.dev for {}", packageURL);
            return metaModel;
        }
        String latestVersion = maybeLatestVersion.get();

        // Collect dependents count for the actual package version (if not possible then the latest version)
        String actualVersion = packageURL.getVersion();
        Optional<Integer> maybeDependents = depsDevApiClient.fetchDependents(system, name, actualVersion)
                .or(() -> depsDevApiClient.fetchDependents(system, name, latestVersion));
        if (maybeDependents.isEmpty()) {
            logger.warn("Could not determine dependents on deps.dev for {}", packageURL);
            // fallthrough
        }
        maybeDependents.ifPresent(metaModel::setDependents);

        return metaModel;
    }
}
