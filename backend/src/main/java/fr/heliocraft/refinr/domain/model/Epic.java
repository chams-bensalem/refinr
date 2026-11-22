// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Epic du backlog : le produit ou un grand domaine fonctionnel, qui regroupe des Features, elles-memes des stories.
 * trackerKey/trackerUrl sont renseignes une fois le ticket cree.
 */
public class Epic {
    private String name;
    private String description;
    private String priority;
    private List<String> labels = new ArrayList<>();
    private List<Feature> features = new ArrayList<>();
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

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    /** Toutes les stories de l'epic, a plat, dans l'ordre de ses Features (copie en lecture seule). */
    public List<Story> getStories() {
        List<Story> stories = new ArrayList<>();
        if (features != null) {
            for (Feature feature : features) {
                if (feature != null && feature.getStories() != null) {
                    stories.addAll(feature.getStories());
                }
            }
        }
        return Collections.unmodifiableList(stories);
    }

    /**
     * Stories sans Feature (forme d'avant le niveau Feature) : une seule Feature, donc implicite, sans nom propre ;
     * null ou vide : aucune Feature.
     */
    public void setStories(List<Story> stories) {
        if (stories == null || stories.isEmpty()) {
            this.features = new ArrayList<>();
            return;
        }
        Feature implicit = new Feature();
        implicit.setStories(new ArrayList<>(stories));
        this.features = new ArrayList<>(List.of(implicit));
    }

    /** Vrai si l'epic n'a qu'une Feature : elle est implicite (pas un niveau distinct quand le gestionnaire le permet). */
    public boolean hasImplicitFeature() {
        return features != null && features.size() == 1;
    }

    /** Vrai si cette Feature est la seule de l'epic. */
    public boolean isImplicit(Feature feature) {
        return hasImplicitFeature() && features.getFirst() == feature;
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
