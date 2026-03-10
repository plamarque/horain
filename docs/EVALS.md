# Maintenance des evals Promptfoo

Les evals sont un **contrat produit** entre l'agent et ses utilisateurs. Ils ne doivent pas évoluer au hasard : ils suivent l'évolution du produit et de ses règles métier.

## 1. Quand ajouter un test

### À chaque nouveau comportement produit

Chaque fois que tu ajoutes une capability à l'agent, ajoute **au moins 3 tests** :

| Type | Objectif |
|------|----------|
| **happy path** | Vérifier le fonctionnement normal |
| **edge case** | Vérifier la robustesse |
| **clarification** | Vérifier le comportement en ambiguïté |

Exemples pour Horain : correction d'entrée, suppression, tags, stats mensuelles, **taux d'occupation (calculs justes et expliqués)**.

### Quand tu corriges un bug IA

Règle prioritaire. Si un bug survient en prod :

1. Écrire un test qui reproduit le bug
2. Corriger le prompt ou le code
3. Vérifier que le test passe

Cela transforme le bug en **test de non-régression permanent**.

### Quand tu observes une dérive du modèle

Les LLM changent (ex. gpt-4.1 → gpt-4.2). Si l'agent répond différemment, oublie de logger, ou renvoie du texte au lieu de JSON : ajoute un test qui capture cette dérive.

### Quand tu enrichis le dataset

Nouveaux projets dans le seed, nouveaux formats de temps, nouvelles langues : ajoute des tests correspondants.

---

## 2. Quand modifier un test existant

Modifier un test seulement dans **trois cas**.

| Cas | Exemple |
|-----|---------|
| **Le produit a changé** | Comportement attendu modifié (ex. unknown project : créer automatiquement au lieu de proposer) |
| **Le test est trop fragile** | Assertions volontairement souples (ex. `icontains-any`) ; avec un seed fixe, on peut durcir |
| **Le test vérifie mal l'intention** | On cherche des mots-clés génériques au lieu du contrat réel (ex. `data.chart.series[]`) |

---

## 3. Quand supprimer un test

Rare mais nécessaire. Supprimer si :

- La feature a disparu
- Le comportement n'est plus supporté
- Le test est redondant avec un autre

---

## 4. Types de tests à maintenir séparément

### Tests déterministes (CI sur chaque PR/push)

Doivent être rapides, déterministes, peu dépendants du modèle. Exécutés avec `promptfooconfig.deterministic.yaml` (ou `./scripts/run-promptfoo-eval.sh --deterministic-only`).

Exemples pour Horain : `log-time`, `clarification`, `robustness`, `analytics`, `json-ui`, `state-transitions`, etc.

### Tests scorés (LLM-as-judge, release uniquement)

Fichiers sous `promptfoo/tests/scored/` : `weekly-summary`, `open-question`, `robustness-scored`, `conversational`. Ils utilisent un modèle Mistral configuré comme juge (`llm-rubric`) pour évaluer la qualité des réponses (exactitude des résumés, cohérence avec les données, robustesse au langage vague, pertinence conversationnelle). Seuils typiques : 0.75 ou 0.8. Exécutés **uniquement en release** (workflow `evals-scored.yml` sur événement release ou tag `v*`), avec `PROMPTFOO_JUDGE_MISTRAL_API_KEY` et `--max-concurrency 1` (limite Mistral 6 req/min). Voir [DEVELOPMENT.md](DEVELOPMENT.md) section Evals scorés.

---

## 5. Règle des 3 tests par capability

| Type | Objectif |
|------|----------|
| happy path | Vérifier le fonctionnement normal |
| edge case | Vérifier la robustesse |
| clarification | Vérifier le comportement en ambiguïté |

---

## 6. Fréquence de maintenance

| Moment | Action |
|--------|--------|
| **À chaque PR / push main** | Exécuter la suite **déterministe** (`npm run eval:deterministic` ou `run-promptfoo-eval.sh --deterministic-only`) pour détecter une régression. Pour ne lancer qu’un test : `--filter-pattern "description"` (voir [DEVELOPMENT.md](DEVELOPMENT.md) Evals Promptfoo). |
| **À chaque release** | Workflow `evals-scored.yml` exécute la suite complète (déterministe + scorée) avec le juge Mistral |
| **À chaque changement de modèle** | Exécuter la suite complète des evals (déterministe + scorée) |
| **Une fois par mois** | Revue rapide : tests inutiles, tests fragiles, cas utilisateurs réels non couverts |

---

## 7. Signal qui doit te faire écrire un test

Si tu te surprends à dire :

> "Ça ne devrait pas faire ça"

→ Écris un test.

---

## 8. Règle tests ≥ prompts

Chaque prompt système important doit avoir **au moins un test**. Idéalement plusieurs.

Les formulations MUST, ALWAYS, NEVER, do NOT, wait for explicit confirmation dans le prompt méritent presque toujours un test dédié.
