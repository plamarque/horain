# Development

## Prérequis

- Node 20+
- npm ou pnpm
- Java 21+ (pour le backend Spring AI)
- Maven ou Gradle

## Installation

```bash
git clone <repo>
cd horain
npm install
# Backend : selon structure (mvn spring-boot:run ou ./gradlew bootRun)
```

## Démarrage en dev

```bash
./scripts/start-dev.sh
```

Backend (8080) + frontend (5173). L’URL réseau local s’affiche pour tester depuis un smartphone sur le même Wi‑Fi.

## Commandes (front-end)

| Commande | Rôle |
|----------|------|
| `npm run dev` | Serveur de développement Vite |
| `npm run build` | Build de production (output: `dist/`) |
| `npm run preview` | Prévisualisation du build |
| `npm run test` | Tests unitaires |
| `npm run test:e2e` | Tests e2e Playwright |

**HTTPS :** Le frontend tourne en HTTPS (mkcert) pour permettre l'accès au micro (reconnaissance vocale).

## Tests e2e

- **Obligation :** Mettre en place et maintenir une suite de tests e2e dès le début du projet.
- **Outil :** Playwright.
- **Exécution :** Intégrée au pipeline CI avant déploiement.

## Publication sur les stores

Voir [docs/PUBLISHING_STORES.md](PUBLISHING_STORES.md) pour le guide complet de publication sur l'App Store et le Play Store (inspiré de [chrono-eps](https://github.com/plamarque/chrono-eps)).

## Migrations de schéma (Flyway)

Le schéma de base de données est géré par **Flyway** et appliqué automatiquement au démarrage du backend.

| Élément | Détail |
|--------|--------|
| **Outil** | Flyway (intégration native Spring Boot) |
| **Emplacement** | `backend/src/main/resources/db/migration/{vendor}/` |
| **PostgreSQL / Supabase** | `db/migration/postgresql/` |
| **H2 (dev local)** | `db/migration/h2/` |
| **Workflow** | 1. Créer `V{n}__description.sql` dans chaque sous-dossier vendor. 2. Mettre à jour [docs/DATA_MODEL.md](DATA_MODEL.md). 3. Commiter. |

**Convention de nommage :** `V<version>__<description>.sql` (ex. `V2__add_source_to_time_logs.sql`). Chaque nouvelle migration incrémente la version.

**À ne pas faire :** modifier une migration déjà appliquée. Créer une nouvelle migration pour tout changement.

## Contribution

- Lire SPEC, DOMAIN et ARCH avant de modifier le comportement ou la structure.
- **Code, commentaires et messages de commit** : toujours en anglais.
- Mettre à jour les docs normatifs quand le comportement ou l'architecture change.
- Garder PLAN et ISSUES factuels.
- Ajouter ou adapter les tests e2e pour les nouvelles fonctionnalités.
