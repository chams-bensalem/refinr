# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-16T18:35:35.671431Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.
- Jeu mesuré : `holdout.en.jsonl` (contrôle 1 traduit en anglais, étiquettes `holdout-labels.csv` reprises telles quelles)
- Langue de sortie : propriété `refinr.llm.output-language` puis détection par story
