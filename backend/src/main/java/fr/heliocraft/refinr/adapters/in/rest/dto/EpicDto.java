package fr.heliocraft.refinr.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** Contrat JSON de l'API REST (noms de proprietes figes, cf. contract/analyze-preview-response.json). */
public class EpicDto {
    private String name;
    private String description;
    private String priority;
    private List<String> labels = new ArrayList<>();
    
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
