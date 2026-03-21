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

**Périodes relatives et `get_current_datetime` :** le chat in-app injecte le bloc **Current server time** (mêmes bornes que l’outil). Les tests déterministes ne doivent pas **imposer** l’appel à `get_current_datetime` pour « cette semaine » / « ce mois » ; ils assertent plutôt les outils métier (`sum_time_for_period`, `get_time_logs_for_period`, graphiques, etc.) ou le contenu de la réponse.

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

Fichiers sous `promptfoo/tests/scored/` : `weekly-summary`, `open-question`, `robustness-scored`, `conversational`. Ils utilisent un modèle Mistral configuré comme juge (`llm-rubric`) pour évaluer la qualité des réponses (exactitude des résumés, cohérence avec les données, robustesse au langage vague, pertinence conversationnelle). Seuils typiques : 0.75 ou 0.8. Exécutés **une fois par release GitHub** : workflow [`evals-scored.yml`](../.github/workflows/evals-scored.yml) sur l’événement **`release` (published)** ou manuellement (`workflow_dispatch`), avec `PROMPTFOO_JUDGE_MISTRAL_API_KEY` et `--max-concurrency 1` (limite Mistral 6 req/min). Voir [DEVELOPMENT.md](DEVELOPMENT.md) section Evals scorés.

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
| **À chaque release** | Quand une **GitHub Release est publiée**, `evals-scored.yml` exécute la suite complète (déterministe + scorée) avec le juge Mistral (pas de second déclenchement sur le tag seul) |
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

---

## 9. Boucle prod → feedback → extraction → triage → Promptfoo

Les réponses de l’agent sont **traçées** en base (`agent_turn`) et le feedback utilisateur (pouce bas / pouce haut) est stocké dans `agent_feedback`. Cette base est une **source de vérité d’incidents et de signaux utilisateur**, pas le dataset d’eval final.

**Workflow recommandé :**

1. **Prod** : chaque tour est persisté ; l’utilisateur peut noter (thumb up/down) sur chaque réponse.
2. **Extraction** : un script (voir [DEVELOPMENT.md](DEVELOPMENT.md) section Export eval candidates) exporte périodiquement les turns avec 👎 ou avec statut d’erreur (tool_error, empty_result, max_iterations) vers un fichier JSONL.
3. **Triage humain** : tous les 👎 ne doivent pas devenir des tests. Filtrer selon : reproductibilité, importance du comportement, récurrence, possibilité de test automatique. Décider pour chaque cas : ignorer, corriger le prompt, créer un test déterministe, créer un test scoré, ou améliorer l’UI.
4. **Promotion** : les cas retenus sont ajoutés au repo sous forme de fichiers Promptfoo (manuel ou script secondaire).

Le script d’extraction ne décide pas quels cas deviennent des tests ; il prépare les candidats pour le triage.

**Plateforme externe (optionnel) :** l’export vers LangSmith (runs, feedback, script dataset) est **optionnel** et ne remplace pas Promptfoo ni les tables `agent_turn` / `agent_feedback` comme source de vérité pour le produit. Voir [ARCH.md](ARCH.md) et [DEVELOPMENT.md](DEVELOPMENT.md) (export vers dataset LangSmith).

**Trace des appels d’outils :** Chaque ligne exportée inclut le champ `tool_calls` (liste des outils exécutés pour ce tour). Chaque entrée contient `name`, `arguments` et `result`. En cas d’échec d’un outil, `result` contient typiquement un JSON avec une clé `error` (ex. `{"error": "Cannot delete project: it has 38 time log entries..."}`). Le triage peut s’appuyer sur cette trace pour comprendre *pourquoi* un tour a échoué ou pourquoi la réponse de l’agent était incorrecte (ex. incohérence entre des `get_time_logs_for_period` vides et un `delete_project` qui signale des entrées existantes), sans avoir à rejouer la conversation.
