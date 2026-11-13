// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * User Story (As a &lt;role&gt;, I want &lt;goal&gt;, So that &lt;benefit&gt;) avec son verdict de preparation
 * pour un agent de code ; epicTrackerKey est la cle de l'epic parent une fois creee.
 */
public class Story {
    private String asA;
    private String iWant;
    private String soThat;
    private String title;
    private String description;
    private List<String> acceptanceCriteria = new ArrayList<>();
    private Integer estimatedStoryPoints;
    private String priority;
    private List<String> labels = new ArrayList<>();
    private List<Task> tasks = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();
    // Jugement de l'analyse : la story est-elle implementable par un agent de code sans decision humaine ?
    private Boolean aiReady;
    private String aiReadyReason;
    private String trackerKey;
    private String trackerUrl;
    private String epicTrackerKey;

    public Story() {}

    public String getAsA() {
        return asA;
    }

    public void setAsA(String asA) {
        this.asA = asA;
    }

    public String getIWant() {
        return iWant;
    }

    public void setIWant(String iWant) {
        this.iWant = iWant;
    }

    public String getSoThat() {
        return soThat;
    }

    public void setSoThat(String soThat) {
        this.soThat = soThat;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public Integer getEstimatedStoryPoints() {
        return estimatedStoryPoints;
    }

    public void setEstimatedStoryPoints(Integer estimatedStoryPoints) {
        this.estimatedStoryPoints = estimatedStoryPoints;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public Boolean getAiReady() {
        return aiReady;
    }

    public void setAiReady(Boolean aiReady) {
        this.aiReady = aiReady;
    }

    public String getAiReadyReason() {
        return aiReadyReason;
    }

    public void setAiReadyReason(String aiReadyReason) {
        this.aiReadyReason = aiReadyReason;
    }

    public String getTrackerKey() {
        return trackerKey;
    }

    public void setTrackerKey(String trackerKey) {
        this.trackerKey = trackerKey;
    }

    public String getTrackerUrl() {
        return trackerUrl;
    }

    public void setTrackerUrl(String trackerUrl) {
        this.trackerUrl = trackerUrl;
    }

    public String getEpicTrackerKey() {
        return epicTrackerKey;
    }

    public void setEpicTrackerKey(String epicTrackerKey) {
        this.epicTrackerKey = epicTrackerKey;
    }
}
