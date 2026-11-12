package fr.heliocraft.refinr.adapters.out.llm;

import java.util.ArrayList;
import java.util.List;

/** Racine de la reponse d'analyse : {@code {"epics": [...]}} (un objet, exige par les modes JSON natifs). */
public class GeneratedBacklog {

    private List<GeneratedEpic> epics = new ArrayList<>();

    public List<GeneratedEpic> getEpics() {
        return epics;
    }

    public void setEpics(List<GeneratedEpic> epics) {
        this.epics = epics;
    }
}
