// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature d'un epic dans le contrat JSON REST (depuis la 1.5). {@code implicit} : seule Feature de son epic (pas un
 * niveau distinct quand le gestionnaire le permet) ; calcule par le serveur, ignore en entree.
 */
public class FeatureDto {
    private String name;
    private String description;
    private String priority;
    private List<String> labels = new ArrayList<>();
    private Boolean implicit;
    private List<StoryDto> stories = new ArrayList<>();

    @JsonProperty("jiraKey")
    private String jiraKey;

    @JsonProperty("jiraUrl")
    private String jiraUrl;

    public FeatureDto() {}

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

    public Boolean getImplicit() {
        return implicit;
    }

    public void setImplicit(Boolean implicit) {
        this.implicit = implicit;
    }

    public List<StoryDto> getStories() {
        return stories;
    }

    public void setStories(List<StoryDto> stories) {
        this.stories = stories;
    }

    public String getJiraKey() {
        return jiraKey;
    }

    public void setJiraKey(String jiraKey) {
        this.jiraKey = jiraKey;
    }

    public String getJiraUrl() {
        return jiraUrl;
    }

    public void setJiraUrl(String jiraUrl) {
        this.jiraUrl = jiraUrl;
    }
}
