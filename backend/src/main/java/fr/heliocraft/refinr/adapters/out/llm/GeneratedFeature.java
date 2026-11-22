// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.util.ArrayList;
import java.util.List;

/** Feature telle que la renvoie le LLM (contenu seulement, jamais de cle de ticket). */
public class GeneratedFeature {
    private String name;
    private String description;
    private String priority;
    private List<String> labels = new ArrayList<>();
    private List<GeneratedStory> stories = new ArrayList<>();

    public GeneratedFeature() {}

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

    public List<GeneratedStory> getStories() {
        return stories;
    }

    public void setStories(List<GeneratedStory> stories) {
        this.stories = stories;
    }
}
