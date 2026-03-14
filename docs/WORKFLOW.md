# Workflow de documentation

Ce document décrit **quand** mettre à jour **quel** document du workflow de gouvernance.

## Règles de mise à jour

| Événement | Document(s) à mettre à jour |
|-----------|-----------------------------|
| Changement de comportement fonctionnel | **SPEC.md** |
| Nouveau concept, terme ou règle métier | **DOMAIN.md** |
| Nouveau composant, technologie ou décision structurelle | **ARCH.md**, éventuellement **docs/ADR/** |
| Changement des outils MCP | **docs/MCP_TOOLS.md** |
| Changement des principes de conception agent/outils/context | **docs/AGENT_DESIGN.md** ; **docs/MCP_TOOLS.md** si la spec des outils change |
| Changement du schéma de données | **docs/DATA_MODEL.md** + migrations Flyway (`db/migration/{vendor}/`) |
| Changement d'UX ou d'interface | **docs/UX.md** |
| Nouvelle décision d'architecture explicite | **docs/ADR/*** (nouveau fichier) |
| Nouvelle tranche, tâche ou jalon | **PLAN.md** |
| Vision long terme, phases post-MVP | **docs/ROADMAP.md** |
| Bug découvert, limitation, travail différé | **ISSUES.md** |
| Nouvelle commande, outil ou étape de setup | **DEVELOPMENT.md** |
| Changement du processus de publication stores | **docs/PUBLISHING_STORES.md** |
| Changement de gouvernance ou de workflow | **AGENTS.md**, **docs/WORKFLOW.md** |
| Nouvelle capability agent ou correction bug IA | **docs/EVALS.md** (si règles évoluent), **promptfoo/tests/** (nouveaux tests) |
| Changement de modèle LLM | Exécuter la suite evals ; ajuster tests si dérive (voir **docs/EVALS.md**) |

## Ordre de lecture recommandé

Pour comprendre le projet :

1. **AGENTS.md** — Vue d'ensemble de la gouvernance
2. **docs/SPEC.md** — Ce que fait le système
3. **docs/DOMAIN.md** — Vocabulaire et règles
4. **docs/ARCH.md** — Structure et technologies
5. **docs/MCP_TOOLS.md** — Outils MCP
6. **docs/AGENT_DESIGN.md** — Principes de conception agents et outils (context engineering)
7. **docs/DATA_MODEL.md** — Schéma base de données
8. **docs/UX.md** — Expérience utilisateur
9. **docs/EVALS.md** — Maintenance des evals Promptfoo
10. **docs/PLAN.md** — État de la livraison
11. **docs/ROADMAP.md** — Vision long terme
12. **docs/ISSUES.md** — Problèmes connus
13. **docs/PUBLISHING_STORES.md** — Publication sur les stores (si applicable)

## Documents normatifs vs suivi

- **Normatifs** : SPEC, DOMAIN, ARCH, MCP_TOOLS, AGENT_DESIGN, DATA_MODEL, UX, EVALS, WORKFLOW, ADRs, AGENTS. Ils définissent le comportement et la structure attendus.
- **Suivi** : PLAN, ROADMAP, ISSUES. Factuels, ne définissent pas le contrat.
- **Opérationnel** : DEVELOPMENT. Workflow développeur.
