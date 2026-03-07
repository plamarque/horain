# Workflow de documentation

Ce document décrit **quand** mettre à jour **quel** document du workflow de gouvernance.

## Règles de mise à jour

| Événement | Document(s) à mettre à jour |
|-----------|-----------------------------|
| Changement de comportement fonctionnel | **SPEC.md** |
| Nouveau concept, terme ou règle métier | **DOMAIN.md** |
| Nouveau composant, technologie ou décision structurelle | **ARCH.md**, éventuellement **docs/ADR/** |
| Changement des outils MCP | **docs/MCP_TOOLS.md** |
| Changement du schéma de données | **docs/DATA_MODEL.md** + migrations Flyway (`db/migration/{vendor}/`) |
| Changement d'UX ou d'interface | **docs/UX.md** |
| Nouvelle décision d'architecture explicite | **docs/ADR/*** (nouveau fichier) |
| Nouvelle tranche, tâche ou jalon | **PLAN.md** |
| Vision long terme, phases post-MVP | **docs/ROADMAP.md** |
| Bug découvert, limitation, travail différé | **ISSUES.md** |
| Nouvelle commande, outil ou étape de setup | **DEVELOPMENT.md** |
| Changement du processus de publication stores | **docs/PUBLISHING_STORES.md** |
| Changement de gouvernance ou de workflow | **AGENTS.md**, **docs/WORKFLOW.md** |

## Ordre de lecture recommandé

Pour comprendre le projet :

1. **AGENTS.md** — Vue d'ensemble de la gouvernance
2. **docs/SPEC.md** — Ce que fait le système
3. **docs/DOMAIN.md** — Vocabulaire et règles
4. **docs/ARCH.md** — Structure et technologies
5. **docs/MCP_TOOLS.md** — Outils MCP
6. **docs/DATA_MODEL.md** — Schéma base de données
7. **docs/UX.md** — Expérience utilisateur
8. **docs/PLAN.md** — État de la livraison
9. **docs/ROADMAP.md** — Vision long terme
10. **docs/ISSUES.md** — Problèmes connus
11. **docs/PUBLISHING_STORES.md** — Publication sur les stores (si applicable)

## Documents normatifs vs suivi

- **Normatifs** : SPEC, DOMAIN, ARCH, MCP_TOOLS, DATA_MODEL, UX, WORKFLOW, ADRs, AGENTS. Ils définissent le comportement et la structure attendus.
- **Suivi** : PLAN, ROADMAP, ISSUES. Factuels, ne définissent pas le contrat.
- **Opérationnel** : DEVELOPMENT. Workflow développeur.
