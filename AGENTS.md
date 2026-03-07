# Agents

Ce document décrit comment les agents (IA et humains) doivent utiliser ce dépôt.

## Sources de vérité (normatives)

Ne pas contredire ces documents. Le code et les changements doivent s'aligner sur eux.

| Document | Rôle |
|----------|------|
| **docs/SPEC.md** | Ce que le système fait ; contrat fonctionnel |
| **docs/DOMAIN.md** | Vocabulaire et règles du domaine |
| **docs/ARCH.md** | Structure et technologies |
| **docs/MCP_TOOLS.md** | Spécification des outils MCP |
| **docs/DATA_MODEL.md** | Schéma base de données |
| **docs/UX.md** | Expérience utilisateur et interface |
| **docs/WORKFLOW.md** | Quand mettre à jour quel document |
| **docs/ADR/** | Décisions d'architecture (un fichier par décision) |

## Suivi et opérationnel (non normatifs)

| Document | Rôle |
|----------|------|
| **docs/PLAN.md** | Livraison : tranches, jalons, statut des tâches. À utiliser pour le progrès, pas pour définir le comportement |
| **docs/ISSUES.md** | Bugs, limitations, travail différé. Suivi des problèmes uniquement |
| **docs/DEVELOPMENT.md** | Installation, commandes, contribution. Opérationnel uniquement |

## Règle MCP

L'agent conversationnel **ne modifie jamais la base de données directement**. Il ne peut accéder aux données que via les outils MCP (list_projects, search_project, create_project, log_time, list_recent_logs). Voir [docs/MCP_TOOLS.md](docs/MCP_TOOLS.md).

## Workflow pour les agents

1. **Lire** SPEC, DOMAIN, ARCH, MCP_TOOLS, DATA_MODEL, UX avant de modifier le comportement ou la structure.
2. **Utiliser** PLAN pour « quoi faire ensuite » et ISSUES pour « ce qui est cassé ou différé ».
3. **Mettre à jour** les docs normatifs quand le comportement ou la structure change ; garder les docs de suivi factuels.
4. **Marquer** hypothèses et incertitudes avec `[ASSUMPTION]` ou `[UNCERTAIN]`.

## Conventions

- **Code, commentaires et commits** : toujours en anglais.
  - Noms de variables, fonctions, classes, messages techniques.
  - Tous les commentaires dans le code.
  - Messages de commit Git.
- Cette règle s'applique indépendamment de la langue utilisée pour la communication (ex. discussion en français).

## Évolution du schéma

- **Migrations :** Toute évolution du schéma passe par des scripts Flyway dans `backend/src/main/resources/db/migration/{vendor}/`. Pas de `ddl-auto: update` ni de modification manuelle de la base.
- **Workflow :** Créer une migration versionnée (ex. `V2__add_column.sql`) dans les dossiers `postgresql/` et `h2/`, puis mettre à jour [docs/DATA_MODEL.md](docs/DATA_MODEL.md).
- **Cohérence :** Les entités JPA et DATA_MODEL.md doivent rester alignés avec le schéma appliqué par Flyway.

## Qualité

- **Tests e2e :** Une suite de tests e2e (Playwright) doit exister et être maintenue dès le début du projet. Les nouvelles fonctionnalités doivent inclure ou adapter les tests e2e correspondants.
