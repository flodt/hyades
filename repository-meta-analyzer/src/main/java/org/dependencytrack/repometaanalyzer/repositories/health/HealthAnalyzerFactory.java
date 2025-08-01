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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Factory for creating health metadata analyzers.
 */
@ApplicationScoped
public class HealthAnalyzerFactory {
    private final Instance<IHealthMetaPackageAnalyzer> packageAnalyzers;
    private final Instance<IHealthMetaSourceCodeAnalyzer> sourceCodeAnalyzers;
    private final Instance<IHealthMetaSourceCodeMapper> sourceCodeMappers;

    @Inject
    public HealthAnalyzerFactory(Instance<IHealthMetaPackageAnalyzer> packageAnalyzers, Instance<IHealthMetaSourceCodeAnalyzer> sourceCodeAnalyzers, Instance<IHealthMetaSourceCodeMapper> sourceCodeMappers) {
        this.packageAnalyzers = packageAnalyzers;
        this.sourceCodeAnalyzers = sourceCodeAnalyzers;
        this.sourceCodeMappers = sourceCodeMappers;
    }

    public List<IHealthMetaPackageAnalyzer> createPackageAnalyzers(PackageURL purl) {
        return packageAnalyzers.stream()
                .filter(Objects::nonNull)
                .filter(pa -> pa.isApplicable(purl))
                .toList();
    }

    public List<IHealthMetaSourceCodeAnalyzer> createSourceCodeAnalyzers(String projectKey) {
        return sourceCodeAnalyzers.stream()
                .filter(Objects::nonNull)
                .filter(sca -> sca.isApplicable(projectKey))
                .toList();
    }

    public List<IHealthMetaSourceCodeMapper> createSourceCodeMappers(PackageURL purl) {
        return sourceCodeMappers.stream()
                .filter(Objects::nonNull)
                .filter(sca -> sca.isApplicable(purl))
                .toList();
    }
}
