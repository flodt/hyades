package org.dependencytrack.repometaanalyzer.model;

import java.util.List;

public class ScoreCardCheck {
    private String name;
    private String description;
    private Float score;
    private String reason;
    private List<String> details;
    private String documentationUrl;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }

    public String getDocumentationUrl() {
        return documentationUrl;
    }

    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }

    @Override
    public String toString() {
        return "ScoreCardCheck{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", score=" + score +
                ", reason='" + reason + '\'' +
                ", details=" + details +
                ", documentationUrl='" + documentationUrl + '\'' +
                '}';
    }
}
