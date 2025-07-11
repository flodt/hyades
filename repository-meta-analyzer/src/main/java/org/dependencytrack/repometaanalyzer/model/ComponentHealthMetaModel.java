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
package org.dependencytrack.repometaanalyzer.model;

import org.dependencytrack.persistence.model.Component;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class ComponentHealthMetaModel implements Serializable {
    private final Component component;

    // Repository and Project (Health) Metadata
    private Integer stars;
    private Integer forks;
    private Integer contributors;
    private Float commitFrequencyWeekly;
    private Integer openIssues;
    private Integer openPRs;
    private Instant lastCommitDate;
    private Integer busFactor;
    private Boolean hasReadme;
    private Boolean hasCodeOfConduct;
    private Boolean hasSecurityPolicy;
    private Integer dependents;
    private Integer files;
    private Boolean isRepoArchived;
    private Integer avgIssueAgeDays;

    // OpenSSF Scorecard: individual checks, overall score, reference version, and timestamp
    private List<ScoreCardCheck> scoreCardChecks;
    private Float scoreCardScore;
    private String scoreCardReferenceVersion;

    private Instant scoreCardTimestamp;

    public ComponentHealthMetaModel(Component component) {
        this.component = component;
    }

    public Component getComponent() {
        return component;
    }

    public Integer getStars() {
        return stars;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public Integer getForks() {
        return forks;
    }

    public void setForks(Integer forks) {
        this.forks = forks;
    }

    public Integer getContributors() {
        return contributors;
    }

    public void setContributors(Integer contributors) {
        this.contributors = contributors;
    }

    public Float getCommitFrequencyWeekly() {
        return commitFrequencyWeekly;
    }

    public void setCommitFrequencyWeekly(Float commitFrequencyWeekly) {
        this.commitFrequencyWeekly = commitFrequencyWeekly;
    }

    public Integer getOpenIssues() {
        return openIssues;
    }

    public void setOpenIssues(Integer openIssues) {
        this.openIssues = openIssues;
    }

    public Integer getOpenPRs() {
        return openPRs;
    }

    public void setOpenPRs(Integer openPRs) {
        this.openPRs = openPRs;
    }

    public Instant getLastCommitDate() {
        return lastCommitDate;
    }

    public void setLastCommitDate(Instant lastCommitDate) {
        this.lastCommitDate = lastCommitDate;
    }

    public Integer getBusFactor() {
        return busFactor;
    }

    public void setBusFactor(Integer busFactor) {
        this.busFactor = busFactor;
    }

    public Boolean getHasReadme() {
        return hasReadme;
    }

    public void setHasReadme(Boolean hasReadme) {
        this.hasReadme = hasReadme;
    }

    public Boolean getHasCodeOfConduct() {
        return hasCodeOfConduct;
    }

    public void setHasCodeOfConduct(Boolean hasCodeOfConduct) {
        this.hasCodeOfConduct = hasCodeOfConduct;
    }

    public Boolean getHasSecurityPolicy() {
        return hasSecurityPolicy;
    }

    public void setHasSecurityPolicy(Boolean hasSecurityPolicy) {
        this.hasSecurityPolicy = hasSecurityPolicy;
    }

    public Integer getDependents() {
        return dependents;
    }

    public void setDependents(Integer dependents) {
        this.dependents = dependents;
    }

    public Integer getFiles() {
        return files;
    }

    public void setFiles(Integer files) {
        this.files = files;
    }

    public Boolean getIsRepoArchived() {
        return isRepoArchived;
    }

    public void setIsRepoArchived(Boolean repoArchived) {
        isRepoArchived = repoArchived;
    }

    public List<ScoreCardCheck> getScoreCardChecks() {
        return scoreCardChecks;
    }

    public void setScoreCardChecks(List<ScoreCardCheck> scoreCardChecks) {
        this.scoreCardChecks = scoreCardChecks;
    }

    public Float getScoreCardScore() {
        return scoreCardScore;
    }

    public void setScoreCardScore(Float scoreCardScore) {
        this.scoreCardScore = scoreCardScore;
    }

    public String getScoreCardReferenceVersion() {
        return scoreCardReferenceVersion;
    }

    public void setScoreCardReferenceVersion(String scoreCardReferenceVersion) {
        this.scoreCardReferenceVersion = scoreCardReferenceVersion;
    }

    public Instant getScoreCardTimestamp() {
        return scoreCardTimestamp;
    }

    public void setScoreCardTimestamp(Instant scoreCardTimestamp) {
        this.scoreCardTimestamp = scoreCardTimestamp;
    }

    public Integer getAvgIssueAgeDays() {
        return avgIssueAgeDays;
    }

    public void setAvgIssueAgeDays(Integer avgIssueAgeDays) {
        this.avgIssueAgeDays = avgIssueAgeDays;
    }

    @Override
    public String toString() {
        return "ComponentHealthMetaModel{" +
                "component=" + component +
                ", stars=" + stars +
                ", forks=" + forks +
                ", contributors=" + contributors +
                ", commitFrequencyWeekly=" + commitFrequencyWeekly +
                ", openIssues=" + openIssues +
                ", openPRs=" + openPRs +
                ", lastCommitDate=" + lastCommitDate +
                ", busFactor=" + busFactor +
                ", hasReadme=" + hasReadme +
                ", hasCodeOfConduct=" + hasCodeOfConduct +
                ", hasSecurityPolicy=" + hasSecurityPolicy +
                ", dependents=" + dependents +
                ", files=" + files +
                ", isRepoArchived=" + isRepoArchived +
                ", avgIssueAgeDays=" + avgIssueAgeDays +
                ", scoreCardChecks=" + scoreCardChecks +
                ", scoreCardScore=" + scoreCardScore +
                ", scoreCardReferenceVersion='" + scoreCardReferenceVersion + '\'' +
                ", scoreCardTimestamp=" + scoreCardTimestamp +
                '}';
    }

    /**
     * Merges this health meta information with the data provided by the other ComponentHealthMetaModel.
     * If present, the information in the other model takes precedence.
     * This object is modified in-place.
     *
     * @param other the other meta model to merge into this one
     */
    public void mergeFrom(ComponentHealthMetaModel other) {
        if (other.stars != null) this.stars = other.stars;
        if (other.forks != null) this.forks = other.forks;
        if (other.contributors != null) this.contributors = other.contributors;
        if (other.commitFrequencyWeekly != null) this.commitFrequencyWeekly = other.commitFrequencyWeekly;
        if (other.openIssues != null) this.openIssues = other.openIssues;
        if (other.openPRs != null) this.openPRs = other.openPRs;
        if (other.lastCommitDate != null) this.lastCommitDate = other.lastCommitDate;
        if (other.busFactor != null) this.busFactor = other.busFactor;
        if (other.hasReadme != null) this.hasReadme = other.hasReadme;
        if (other.hasCodeOfConduct != null) this.hasCodeOfConduct = other.hasCodeOfConduct;
        if (other.hasSecurityPolicy != null) this.hasSecurityPolicy = other.hasSecurityPolicy;
        if (other.dependents != null) this.dependents = other.dependents;
        if (other.files != null) this.files = other.files;
        if (other.isRepoArchived != null) this.isRepoArchived = other.isRepoArchived;
        if (other.scoreCardChecks != null) this.scoreCardChecks = other.scoreCardChecks;
        if (other.scoreCardScore != null) this.scoreCardScore = other.scoreCardScore;
        if (other.scoreCardReferenceVersion != null) this.scoreCardReferenceVersion = other.scoreCardReferenceVersion;
        if (other.scoreCardTimestamp != null) this.scoreCardTimestamp = other.scoreCardTimestamp;
        if (other.avgIssueAgeDays != null) this.avgIssueAgeDays = other.avgIssueAgeDays;
    }
}
