// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ContextRequest;
import fr.heliocraft.refinr.domain.model.ProjectContext;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;

public class ReassessStoryService implements ReassessStory {

    private final IssueTrackerPort issueTracker;
    private final ReadinessAssessorPort assessor;
    private final OutputLanguagePolicy languagePolicy;
    // Contexte projet (X1), ou null
    private final ProjectContextResolver contexts;

    public ReassessStoryService(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor) {
        this(issueTracker, assessor, OutputLanguagePolicy.DETECT);
    }

    public ReassessStoryService(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor,
                                OutputLanguagePolicy languagePolicy) {
        this(issueTracker, assessor, languagePolicy, null);
    }

    /** @param contexts contexte projet (X1) ; null : aucun contexte (une demande de contexte est refusee) */
    public ReassessStoryService(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor,
                                OutputLanguagePolicy languagePolicy, ProjectContextResolver contexts) {
        this.contexts = contexts;
        this.languagePolicy = languagePolicy;
        this.issueTracker = issueTracker;
        this.assessor = assessor;
    }

    @Override
    public Readiness reassess(String storyKey, String precision, Language requested) {
        return reassess(storyKey, precision, requested, ContextRequest.NONE);
    }

    @Override
    public Readiness reassess(String storyKey, String precision, Language requested, ContextRequest contextRequest) {
        if (contexts == null && !contextRequest.isEmpty()) {
            throw new UnsupportedOperationException("project context not configured");
        }
        if (contexts != null) {
            contexts.check(contextRequest);
        }
        TrackedStory story = issueTracker.findStory(storyKey);
        // Seules les stories en attente de validation sont reevaluees : une story ai-ready ne regresse pas ici
        if (!ReadinessPolicy.isAwaitingValidation(story.labels())) {
            throw new StoryNotAwaitingValidationException(storyKey);
        }
        Language language = languagePolicy.resolve(requested, story.summary() + "\n" + story.description());
        // B3 : lignes de verdict retirees du texte envoye (aucun ancrage sur un verdict precedent) ; la derniere question
        // ouverte passe dans un champ a part ; les precisions precedentes restent dans le texte
        String description = fr.heliocraft.refinr.domain.service.StoryMarkdown.forModel(story.description());
        String previousQuestion = fr.heliocraft.refinr.domain.service.StoryMarkdown.lastOpenQuestion(story.description());
        String summary = fr.heliocraft.refinr.domain.service.StoryMarkdown.decodeEntities(story.summary());
        // Contexte de la requete, sinon celui du projet du ticket, sinon celui par defaut
        ProjectContext context = contexts == null ? ProjectContext.NONE : contexts.resolve(contextRequest, story.project());
        ReadinessAssessorPort assessor = context.isPresent() ? this.assessor.withContext(context.text()) : this.assessor;
        Readiness verdict = LlmFailures.translate(() -> assessor.reassess(summary, description, precision, previousQuestion,
                language));
        issueTracker.applyReassessment(story, precision, verdict, language);
        return verdict;
    }
}
