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
import org.dependencytrack.persistence.model.Component;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;

import java.time.Instant;
import java.util.Collections;

public class DummyHealthMetaAnalyzer extends AbstractHealthMetaAnalyzer {
    @Override
    public boolean isApplicable(PackageURL packageURL) {
        return true;
    }

    @Override
    public ComponentHealthMetaModel analyze(PackageURL packageURL) {
        // TODO: remove me

        Component component = new Component();
        component.setPurl(packageURL);

        ComponentHealthMetaModel metaModel = new ComponentHealthMetaModel(component);

        // dummy values
        metaModel.setStars(42);
        metaModel.setForks(7);
        metaModel.setContributors(3);
        metaModel.setCommitFrequencyWeekly(1.5f);
        metaModel.setOpenIssues(5);
        metaModel.setOpenPRs(2);
        metaModel.setLastCommitDate(Instant.now());
        metaModel.setBusFactor(1);

        metaModel.setHasReadme(true);
        metaModel.setHasCodeOfConduct(false);
        metaModel.setHasSecurityPolicy(false);

        metaModel.setDependents(0);
        metaModel.setFiles(10);
        metaModel.setIsRepoArchived(false);

        metaModel.setScoreCardChecks(Collections.emptyList());
        metaModel.setScoreCardScore(0.0f);
        metaModel.setScoreCardReferenceVersion("0.1.0");
        metaModel.setScoreCardTimestamp(Instant.now());

        metaModel.setAvgIssueAgeDays(1234);

        return metaModel;
    }
}
