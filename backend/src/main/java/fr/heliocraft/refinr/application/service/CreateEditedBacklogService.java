// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/**
 * Creation d'un backlog edite par l'utilisateur : le verdict est recalcule sur le contenu final de chaque story
 * (celui envoye par le client est ignore), puis le backlog est cree.
 */
public class CreateEditedBacklogService implements CreateEditedBacklog {

    private final ReadinessAssessorPort assessor;
    private final CreateBacklog createBacklog;
    private final OutputLanguagePolicy languagePolicy;
    // Contexte projet (X1), ou null
    private final ProjectContextResolver contexts;

    public CreateEditedBacklogService(ReadinessAssessorPort assessor, CreateBacklog createBacklog) {
        this(assessor, createBacklog, OutputLanguagePolicy.DETECT);
    }

    public CreateEditedBacklogService(ReadinessAssessorPort assessor, CreateBacklog createBacklog,
                                      OutputLanguagePolicy languagePolicy) {
        this(assessor, createBacklog, languagePolicy, null);
    }

    /** @param contexts contexte projet (X1) ; null : la requete seule (sans source documentaire : texte seulement) */
    public CreateEditedBacklogService(ReadinessAssessorPort assessor, CreateBacklog createBacklog,
                                      OutputLanguagePolicy languagePolicy, ProjectContextResolver contexts) {
        this.contexts = contexts;
        this.languagePolicy = languagePolicy;
        this.assessor = assessor;
        this.createBacklog = createBacklog;
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project, Language language) {
        return create(epics, project, language, fr.heliocraft.refinr.application.port.in.ContextRequest.NONE);
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project, Language language,
                                        fr.heliocraft.refinr.application.port.in.ContextRequest context) {
        if (contexts == null && !context.isEmpty()) {
            throw new UnsupportedOperationException("project context not configured");
        }
        if (contexts != null) {
            contexts.check(context);
        }
        // Cas de test envoyes par le client : controles avant tout appel (modele ou gestionnaire), 400 sinon
        for (int e = 0; e < epics.size(); e++) {
            List<fr.heliocraft.refinr.domain.model.Story> stories = epics.get(e) != null ? epics.get(e).getStories() : List.of();
            for (int s = 0; s < stories.size(); s++) {
                if (stories.get(s) == null) {
                    continue;
                }
                String position = (e + 1) + "." + (s + 1);
                String violation = fr.heliocraft.refinr.domain.service.TestCasePolicy.violation(stories.get(s), position);
                if (violation != null) {
                    throw new fr.heliocraft.refinr.application.port.in.InvalidRequestException(violation);
                }
                // Story qui a des cas de test : chaque critere en a 1 a 3 (sans cas de test : creee sans section)
                List<fr.heliocraft.refinr.domain.model.TestCase> testCases = stories.get(s).getTestCases();
                if (testCases != null && !testCases.isEmpty()) {
                    int criteria = stories.get(s).getAcceptanceCriteria() != null
                            ? stories.get(s).getAcceptanceCriteria().size() : 0;
                    List<String> problems = fr.heliocraft.refinr.domain.service.TestCasePolicy.coverageProblems(testCases, criteria);
                    if (!problems.isEmpty()) {
                        throw new fr.heliocraft.refinr.application.port.in.InvalidRequestException(
                                "testCases of story " + position + " do not cover its acceptance criteria: "
                                        + String.join("; ", problems));
                    }
                }
            }
        }
        fr.heliocraft.refinr.domain.model.ProjectContext projectContext = contexts == null
                ? fr.heliocraft.refinr.domain.model.ProjectContext.NONE : contexts.resolve(context, project);
        ReadinessAssessorPort assessor = projectContext.isPresent() ? this.assessor.withContext(projectContext.text())
                : this.assessor;
        // Sans langue demandee : la propriete, puis la detection story par story
        StoryVerdicts.apply(epics, assessor, story -> languagePolicy.resolve(language, StoryText.of(story)));
        return createBacklog.create(epics, project);
    }
}
