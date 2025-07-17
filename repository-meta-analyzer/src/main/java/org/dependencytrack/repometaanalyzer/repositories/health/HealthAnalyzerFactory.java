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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Factory for creating health metadata analyzers.
 */
@ApplicationScoped
public class HealthAnalyzerFactory {
    private static final List<Supplier<IHealthMetaAnalyzer>> healthAnalyzers = List.of(
            DepsDevGitHubHealthMetaAnalyzer::new
    );

    /**
     * Return all applicable health analyzers for the given package URL
     * @param purl the package URL
     * @return list of all applicable analyzers
     */
    public List<IHealthMetaAnalyzer> createApplicableAnalyzers(PackageURL purl) {
        List<IHealthMetaAnalyzer> analyzers = new ArrayList<>();

        // initialize and check analyzers
        for (Supplier<IHealthMetaAnalyzer> supplier : healthAnalyzers) {
            IHealthMetaAnalyzer analyzer = supplier.get();
            if (analyzer != null && analyzer.isApplicable(purl)) {
                analyzers.add(analyzer);
            }
        }

        return analyzers;
    }
}
