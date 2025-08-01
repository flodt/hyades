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
package org.dependencytrack.repometaanalyzer.repositories.health.sourcemapper;

import com.github.packageurl.PackageURL;

import java.util.Optional;

public interface IHealthMetaSourceCodeMapper {
    /**
     * Returns if the analyzer is applicable for the component
     * @param packageURL the package URL
     * @return true if applicable, else false
     */
    boolean isApplicable(PackageURL packageURL);

    /**
     * Analyzes the component and returns the source code project key identifying the VCS where this component's
     * source code is located.
     * @param packageURL the PURL of the component to analyze
     * @return Optional containing the project key of the source code, e.g. github.com/owner/repo
     */
    Optional<String> findSourceCodeFor(PackageURL packageURL);
}
