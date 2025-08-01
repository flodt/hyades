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
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
public final class HealthMetaAnalyzer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final HealthAnalyzerFactory healthAnalyzerFactory;

    @Inject
    public HealthMetaAnalyzer(HealthAnalyzerFactory healthAnalyzerFactory) {
        this.healthAnalyzerFactory = healthAnalyzerFactory;
    }

    public boolean isApplicable(PackageURL packageURL) {
        return !healthAnalyzerFactory.createPackageAnalyzers(packageURL).isEmpty()
                || !healthAnalyzerFactory.createSourceCodeMappers(packageURL).isEmpty();
    }

    public ComponentHealthMetaModel analyze(PackageURL packageURL) {
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

        // We also use the legacy data models here for consistency with the other analyzers.
        // This should also make it easier to swap out the communication infrastructure later on.
        Component component = new Component();
        component.setPurl(packageURL);
        ComponentHealthMetaModel mergedResults = new ComponentHealthMetaModel(component);

        // run applicable package analyzers
        healthAnalyzerFactory.createPackageAnalyzers(packageURL)
                .stream()
                .map(pa -> pa.analyze(packageURL))
                .forEach(mergedResults::mergeFrom);

        // then try to map to VCS
        Optional<String> maybeProjectKey = healthAnalyzerFactory.createSourceCodeMappers(packageURL)
                .stream()
                .map(scm -> scm.findSourceCodeFor(packageURL))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (maybeProjectKey.isEmpty()) {
            logger.info("Could not find source code project for {}", packageURL.toString());
            return mergedResults;
        }

        // then analyze the source code project
        String projectKey = maybeProjectKey.get();
        healthAnalyzerFactory.createSourceCodeAnalyzers(projectKey)
                .stream()
                .map(sca -> sca.analyze(projectKey))
                .forEach(mergedResults::mergeFrom);

        return mergedResults;
    }
}
