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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

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

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // random integers
        metaModel.setStars(rnd.nextInt(0, 5000));                   // 0–4999 stars
        metaModel.setForks(rnd.nextInt(0, 2000));                   // 0–1999 forks
        metaModel.setContributors(rnd.nextInt(1, 100));             // 1–99 contributors
        metaModel.setOpenIssues(rnd.nextInt(0, 1000));              // 0–999 open issues
        metaModel.setOpenPRs(rnd.nextInt(0, 500));                  // 0–499 open PRs
        metaModel.setBusFactor(rnd.nextInt(1, 10));                 // 1–9 bus factor
        metaModel.setDependents(rnd.nextInt(0, 1000));              // 0–999 dependents
        metaModel.setFiles(rnd.nextInt(1, 500));                    // 1–499 files
        metaModel.setAvgIssueAgeDays(rnd.nextInt(0, 2000));         // 0–1999 days

        // random floats
        metaModel.setCommitFrequencyWeekly(rnd.nextFloat() * 10f);  // 0.0–10.0 commits/week
        metaModel.setScoreCardScore(rnd.nextFloat() * 10f);         // 0.0–10.0 score

        // random dates within the last 365 days
        long daysAgo = rnd.nextLong(0, 366);
        metaModel.setLastCommitDate(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        long scDaysAgo = rnd.nextLong(0, 366);
        metaModel.setScoreCardTimestamp(Instant.now().minus(scDaysAgo, ChronoUnit.DAYS));
        metaModel.setScoreCardReferenceVersion("0." + rnd.nextInt(1, 5) + "." + rnd.nextInt(0, 10));

        // random booleans
        metaModel.setHasReadme(rnd.nextBoolean());
        metaModel.setHasCodeOfConduct(rnd.nextBoolean());
        metaModel.setHasSecurityPolicy(rnd.nextBoolean());
        metaModel.setIsRepoArchived(rnd.nextBoolean());

        // leave scoreCardChecks empty or fill with dummy strings
        metaModel.setScoreCardChecks(Collections.emptyList());

        return metaModel;
    }
}
