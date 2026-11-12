package fr.heliocraft.refinr.analyzer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** Forme JSON attendue dans la reponse du LLM (memes proprietes que le contrat REST, inconnues refusees). */
public class GeneratedTask {
    private String title;
    private String description;
    
    @JsonProperty("estimatedHours")
    private Integer estimatedHours;
    
    private List<String> labels = new ArrayList<>();
    
    @JsonProperty("jiraKey")
    private String jiraKey;
    
    @JsonProperty("jiraUrl")
    private String jiraUrl;
    
    @JsonProperty("parentStoryKey")
    private String parentStoryKey;
    
    // Constructors
    public GeneratedTask() {}
    
    // Getters and Setters
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

    public String getParentStoryKey() {
        return parentStoryKey;
    }

    public void setParentStoryKey(String parentStoryKey) {
        this.parentStoryKey = parentStoryKey;
    }
}
