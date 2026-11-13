// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.ArrayList;
import java.util.List;

/** Sous-tache d'une story ; parentTrackerKey est la cle de la story parente une fois creee. */
public class Task {
    private String title;
    private String description;
    private Integer estimatedHours;
    private List<String> labels = new ArrayList<>();
    private String trackerKey;
    private String trackerUrl;
    private String parentTrackerKey;

    public Task() {}

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

    public Integer getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(Integer estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
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

    public String getParentTrackerKey() {
        return parentTrackerKey;
    }

    public void setParentTrackerKey(String parentTrackerKey) {
        this.parentTrackerKey = parentTrackerKey;
    }
}
