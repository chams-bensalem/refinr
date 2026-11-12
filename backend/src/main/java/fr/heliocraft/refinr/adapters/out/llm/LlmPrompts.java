package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Story;

import java.util.List;

/** Prompts envoyes aux modeles (le format de sortie est en plus impose par le schema JSON). */
final class LlmPrompts {

    static final String SYSTEM = "Tu es un expert Scrum Master et Product Owner. Tu reponds uniquement par un objet JSON.";

    private static final String READINESS_RULES = """
            - aiReady = true UNIQUEMENT si un agent de code peut implémenter la story sans aucune décision humaine:
              périmètre clair, critères d'acceptation testables, pas d'ambiguïté fonctionnelle, pas de dépendance
              externe non précisée (accès, clé d'API, maquette, choix produit à trancher)
            - Sinon aiReady = false
            - aiReadyReason: une phrase qui justifie le verdict (ce qui manque encore si false)
            """;

    private LlmPrompts() {}

    static String analysis(String documentContent) {
        return """
            MISSION: Analyser le cahier des charges complet ci-dessous et créer une structure Scrum DÉTAILLÉE.

            ÉCHELLE (PROPORTIONNELLE AU DOCUMENT):
            - Besoin court (quelques phrases, une seule fonctionnalité): 1 seul Epic, 1 à 5 User Stories, 1 à 4 tasks par story
            - Cahier des charges complet multi-modules: un Epic par module réellement décrit, 3 à 8 User Stories par Epic, 2 à 5 tasks par story
            - Ne JAMAIS inventer de fonctionnalités, modules ou exigences absents du document: la taille de la sortie suit la taille du besoin

            RÈGLES STRICTES:
            1. Créer un Epic pour chaque module/fonctionnalité réellement mentionné dans le document (voir ÉCHELLE)
            2. Format Story: "As a <role>, I want <goal>, So that <benefit>"
            3. Priorités réalistes: High (critique), Medium (important), Low (nice-to-have)
            4. Labels techniques précis: backend, frontend, devops, database, api, security, kafka, etc.
            5. Acceptance Criteria clairs et testables (2-5 par story)

            PRÊTE POUR UN AGENT DE CODE (champs 'aiReady' et 'aiReadyReason' sur chaque Story):
            - aiReady = true UNIQUEMENT si un agent de code peut implémenter la story sans aucune décision humaine:
              périmètre clair, critères d'acceptation testables, pas d'ambiguïté fonctionnelle, pas de dépendance
              externe non précisée (accès, clé d'API, maquette, choix produit à trancher)
            - Sinon aiReady = false
            - aiReadyReason: une phrase qui justifie le verdict (ce qui manque si false)

            ESTIMATION DES STORY POINTS (Suite de Fibonacci):
            Pour chaque Story, estime la complexité et assigne des story points:
            - 1: Très simple (< 2h)
            - 2: Simple (2-4h)
            - 3: Moyen (1 jour)
            - 5: Complexe (2-3 jours)
            - 8: Très complexe (1 semaine)
            - 13: Epic-level (> 1 semaine)

            FORMAT DES ACCEPTANCE CRITERIA (Given/When/Then):
            Pour chaque Acceptance Criteria, utilise le format Given/When/Then:
            - Given: Contexte initial
            - When: Action de l'utilisateur
            - Then: Résultat attendu

            Exemple: "Given l'utilisateur est connecté, When il clique sur 'Analyser', Then le système affiche les résultats en moins de 2 secondes"

            IMPORTANT:
            - LIS TOUT LE DOCUMENT ATTENTIVEMENT
            - Crée des Epics pour TOUS les modules mentionnés
            - Utilise les informations RÉELLES du document (noms de services, technologies, architecture)
            - NE PAS utiliser d'exemples génériques
            - Sois précis et technique
            - Ajoute le champ 'estimatedStoryPoints' dans chaque Story

            DOCUMENT À ANALYSER:
            """ + documentContent + """

            Réponds UNIQUEMENT en JSON valide (pas de markdown, pas de texte avant/après):
            {
              "epics": [
                {
                  "name": "Epic: [Nom exact du module du document]",
                  "description": "[Description détaillée basée sur le document]",
                  "priority": "High",
                  "labels": ["backend", "api", "kafka"],
                  "stories": [
                    {
                      "asA": "Developer",
                      "iWant": "Set up the GitLab connector",
                      "soThat": "The system can index repositories",
                      "title": "GitLab Integration Setup",
                      "description": "Implement OAuth2 connection to GitLab",
                      "acceptanceCriteria": [
                        "Given l'utilisateur a un token GitLab valide, When il soumet le token, Then le système authentifie et affiche 'Connexion réussie'",
                        "Given un token invalide est soumis, When le système valide les permissions, Then un message d'erreur explicite est affiché",
                        "Given l'utilisateur est authentifié, When il accède à la page des repos, Then la liste des repositories accessibles s'affiche en moins de 3 secondes"
                      ],
                      "estimatedStoryPoints": 5,
                      "priority": "High",
                      "labels": ["backend", "api", "security"],
                      "dependencies": [],
                      "aiReady": false,
                      "aiReadyReason": "Le fournisseur OAuth2 et les scopes attendus ne sont pas precises",
                      "tasks": [
                        {
                          "title": "Create GitLabConnector service",
                          "description": "Implement Spring service with OAuth2 logic",
                          "estimatedHours": 4,
                          "labels": ["backend", "spring"]
                        },
                        {
                          "title": "Add token validation",
                          "description": "Verify token scopes and permissions",
                          "estimatedHours": 2,
                          "labels": ["backend", "security"]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """;
    }

    static String verdict(Story story) {
        List<String> criteria = story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria() : List.of();
        return """
            MISSION: Dire si la User Story Jira ci-dessous est PRÊTE POUR UN AGENT DE CODE.
            """ + READINESS_RULES + """

            STORY:
            Titre: """ + story.getTitle() + """

            Description:
            """ + (story.getDescription() != null ? story.getDescription() : "") + """

            Critères d'acceptation:
            """ + String.join("\n", criteria) + """

            Réponds UNIQUEMENT en JSON valide (pas de markdown, pas de texte avant/après):
            {"aiReady": false, "aiReadyReason": "..."}
            """;
    }

    static String reassessment(String summary, String description, String precision) {
        return """
            MISSION: Une User Story Jira a été jugée NON prête pour un agent de code. L'utilisateur vient
            d'apporter une précision. Réévalue la story en tenant compte de cette précision.

            PRÊTE POUR UN AGENT DE CODE:
            """ + READINESS_RULES + """
            - La précision de l'utilisateur fait foi sur ce qu'elle tranche ; ne suppose rien d'autre

            STORY:
            Titre: """ + summary + """

            Description:
            """ + description + """

            PRÉCISION DE L'UTILISATEUR:
            """ + precision + """

            Réponds UNIQUEMENT en JSON valide (pas de markdown, pas de texte avant/après):
            {"aiReady": false, "aiReadyReason": "..."}
            """;
    }
}
