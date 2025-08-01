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
import org.dependencytrack.repometaanalyzer.repositories.health.api.DepsDevApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class DepsDevHealthMetaSourceCodeAnalyzer implements IHealthMetaSourceCodeAnalyzer {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    DepsDevApiClient depsDevApiClient;

    @Override
    public boolean isApplicable(String projectKey) {
        if (projectKey == null || projectKey.isEmpty()) return false;
        return Stream.of("github", "bitbucket", "gitlab").anyMatch(projectKey::contains);
    }

    @Override
    public ComponentHealthMetaModel analyze(PackageURL packageURL, String projectKey) {
        Component component = new Component();
        component.setPurl(packageURL);
        ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(component);

        // Collect OpenSSF Scorecard for this project
        Optional<ComponentHealthMetaModel> maybeScorecardStarsForks = depsDevApiClient.fetchScorecardAndStarsForksIssuesForProject(projectKey);
        if (maybeScorecardStarsForks.isEmpty()) {
            logger.info("Could not determine scorecard for {}", packageURL);
            // we can continue with the GitHub API even without the scorecard; fallthrough
        }
        maybeScorecardStarsForks.ifPresent(metaModel::mergeFrom);

        return metaModel;
    }
}
