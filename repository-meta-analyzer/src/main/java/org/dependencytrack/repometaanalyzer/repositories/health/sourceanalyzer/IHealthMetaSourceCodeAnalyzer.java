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
package org.dependencytrack.repometaanalyzer.repositories.health.sourceanalyzer;

import com.github.packageurl.PackageURL;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;

public interface IHealthMetaSourceCodeAnalyzer {
    /**
     * Returns if the analyzer is applicable for the component
     * @param projectKey the project key identifying the source code repository (e.g., github.com/owner/repo)
     * @return true if applicable, else false
     */
    boolean isApplicable(String projectKey);

    /**
     * Analyzes the component and returns the partial health metadata
     *
     * @param packageURL the PURL for the project to analyze
     * @param projectKey the project key identifying the source code repository (e.g., github.com/owner/repo)
     * @return the health metadata retrieved for the component
     */
    ComponentHealthMetaModel analyze(PackageURL packageURL, String projectKey);
}
