# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-23T23:15:15.076584Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.
- Langue de sortie : propriété `refinr.llm.output-language` puis détection par story
- Contexte projet : `eval/context/answering.md` (2092 caractères, sha256 cd30b39e1ffd) ; aucun cache : chaque exécution appelle le modèle
