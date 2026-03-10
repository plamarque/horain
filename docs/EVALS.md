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

Exemples pour Horain : correction d'entrée, suppression, tags, stats mensuelles.

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

### Tests stables (CI)

Doivent être rapides, déterministes, peu dépendants du modèle.

Exemples pour Horain : `log-time`, `clarification`, `robustness`.

### Tests exploratoires (hors CI ou avant release)

Plus flexibles : `analytics`, `multi-turn`, `json-ui`. Ils dépendent davantage du modèle ou du contexte.

Souvent lancés avant release ou après upgrade modèle.

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
| **À chaque PR** | Exécuter `promptfoo eval` pour détecter une régression |
| **À chaque changement de modèle** | Exécuter la suite complète des evals |
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
