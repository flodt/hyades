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
import org.apache.http.impl.client.CloseableHttpClient;
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;

public interface IHealthMetaAnalyzer {
    /**
     * Sets the {@link CloseableHttpClient} to be used by the analyzer.
     *
     * @param httpClient The {@link CloseableHttpClient} to use
     */
    void setHttpClient(final CloseableHttpClient httpClient);

    /**
     * Returns if the analyzer is applicable for the component
     * @param packageURL the package URL
     * @return true if applicable, else false
     */
    boolean isApplicable(PackageURL packageURL);

    /**
     * Returns the name of the analyzer
     * @return the analyzer name
     */
    String getName();

    /**
     * Analyzes the component and returns the health metadata
     * @param component the component to analyze
     * @return the health metadata retrieved for the component
     */
    ComponentHealthMetaModel analyze(Component component);
}
