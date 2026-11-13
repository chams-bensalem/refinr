// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.ArrayList;
import java.util.List;

/** Epic du backlog : regroupe des stories ; trackerKey/trackerUrl sont renseignes une fois le ticket cree. */
public class Epic {
    private String name;
    private String description;
    private String priority;
    private List<String> labels = new ArrayList<>();
    private List<Story> stories = new ArrayList<>();
    private String trackerKey;
    private String trackerUrl;

    public Epic() {}

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

    public List<Story> getStories() {
        return stories;
    }

    public void setStories(List<Story> stories) {
        this.stories = stories;
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
}
