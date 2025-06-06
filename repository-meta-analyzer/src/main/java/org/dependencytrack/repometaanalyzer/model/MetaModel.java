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
import java.util.Date;
import java.util.List;

public class MetaModel implements Serializable {

    private Component component;
    private String latestVersion;
    private Date publishedTimestamp;

    // Repository and Project (Health) Metadata
    private Integer stars;
    private Integer forks;
    private Integer contributors;
    private Float commitFrequency;
    private Integer openIssues;
    private Integer openPRs;
    private String lastCommitDate;
    private Integer busFactor;
    private Boolean hasReadme;
    private Boolean hasCodeOfConduct;
    private Boolean hasSecurityPolicy;
    private Integer dependents;
    private Integer versions;
    private Integer files;

    // OpenSSF Scorecard: indivual checks, overall score and reference version
    private List<ScoreCardCheck> scoreCardChecks;
    private Float scoreCardScore;
    private String scoreCardReferenceVersion;

    public MetaModel(){
    }
    public MetaModel(final Component component) {
        this.component = component;
    }

    public Component getComponent() {
        return component;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(final String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public Date getPublishedTimestamp() {
        return publishedTimestamp;
    }

    public void setPublishedTimestamp(final Date publishedTimestamp) {
        this.publishedTimestamp = publishedTimestamp;
    }

    public void setComponent(Component component) {
        this.component = component;
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

    public Float getCommitFrequency() {
        return commitFrequency;
    }

    public void setCommitFrequency(Float commitFrequency) {
        this.commitFrequency = commitFrequency;
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

    public String getLastCommitDate() {
        return lastCommitDate;
    }

    public void setLastCommitDate(String lastCommitDate) {
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

    public Integer getVersions() {
        return versions;
    }

    public void setVersions(Integer versions) {
        this.versions = versions;
    }

    public Integer getFiles() {
        return files;
    }

    public void setFiles(Integer files) {
        this.files = files;
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
}
