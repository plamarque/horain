# ADR 0001: Flyway pour les migrations de schéma

## Status

Accepted.

## Context

Le schéma de base de données doit évoluer de manière contrôlée, versionnée et reproductible. Horain utilise Supabase (PostgreSQL) en production et H2 en mémoire pour le développement local.

## Decision

Nous utilisons **Flyway** pour gérer les migrations de schéma.

- Intégration native Spring Boot (auto-configuration, exécution au démarrage).
- Scripts SQL versionnés (`V1__description.sql`).
- Compatible PostgreSQL et Supabase.
- Migrations par dialecte via `db/migration/{vendor}/` (postgresql, h2).
- JPA configuré avec `ddl-auto: validate` pour éviter toute modification automatique du schéma par Hibernate.

## Consequences

- Chaque changement de schéma nécessite une nouvelle migration dans `postgresql/` et `h2/`.
- DATA_MODEL.md et les entités JPA doivent rester alignés avec les migrations.
- Hibernate ne modifie plus le schéma ; Flyway en est la seule source de vérité.
