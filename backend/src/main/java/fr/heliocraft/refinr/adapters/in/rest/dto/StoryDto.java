package fr.heliocraft.refinr.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/** Contrat JSON de l'API REST (noms de proprietes figes, cf. contract/analyze-preview-response.json). */
public class StoryDto {
    // User Story format: As a <role>, I want <goal>, So that <benefit>
    
    @JsonProperty("asA")
    private String asA;
    
    @JsonProperty("iWant")
    private String iWant;
    
    @JsonProperty("soThat")
    private String soThat;
    
    private String title;
    private String description;
    private List<String> acceptanceCriteria = new ArrayList<>();

    @JsonProperty("estimatedStoryPoints")
    private Integer estimatedStoryPoints;

    private String priority;
    private List<String> labels = new ArrayList<>();
    private List<TaskDto> tasks = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();
    
    // Jira fields
    private String jiraKey;
    private String jiraUrl;
    private String epicKey;
    
    public StoryDto() {}
    
    // Getters and Setters
    @JsonProperty("asA")
    public String getAsA() {
        return asA;
    }

    @JsonProperty("asA")
    public void setAsA(String asA) {
        this.asA = asA;
    }

    @JsonProperty("iWant")
    public String getIWant() {
        return iWant;
    }

    @JsonProperty("iWant")
    public void setIWant(String iWant) {
        this.iWant = iWant;
    }

    @JsonProperty("soThat")
    public String getSoThat() {
        return soThat;
    }

    @JsonProperty("soThat")
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

    public List<TaskDto> getTasks() {
        return tasks;
    }

    public void setTasks(List<TaskDto> tasks) {
        this.tasks = tasks;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    // Jugement de l'analyse : la story est-elle implementable par un agent de code sans decision humaine ?
    @JsonProperty("aiReady")
    private Boolean aiReady;

    @JsonProperty("aiReadyReason")
    private String aiReadyReason;

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

    public String getEpicKey() {
        return epicKey;
    }

    public void setEpicKey(String epicKey) {
        this.epicKey = epicKey;
    }
}
