// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrat JSON de l'API REST (noms de proprietes figes, cf. contract/analyze-preview-response.json). Depuis la 1.5 :
 * {@code features} (epic -> features -> stories) ; {@code stories} garde toutes les stories de l'epic a plat (forme
 * d'avant, depreciee, gardee au moins jusqu'a la 2.0 pour l'interface web et le plugin Obsidian 0.2.0).
 */
public class EpicDto {
    private String name;
    private String description;
    private String priority;
    private List<String> labels = new ArrayList<>();

    // null = absent de la requete (client d'avant la 1.5 : seulement stories)
    private List<FeatureDto> features;

    @JsonProperty("stories")
    private List<StoryDto> stories = new ArrayList<>();
    
    @JsonProperty("jiraKey")
    private String jiraKey;
    
    @JsonProperty("jiraUrl")
    private String jiraUrl;
    
    // Constructors
    public EpicDto() {}
    
    // Getters and Setters
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

    public List<FeatureDto> getFeatures() {
        return features;
    }

    public void setFeatures(List<FeatureDto> features) {
        this.features = features;
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
