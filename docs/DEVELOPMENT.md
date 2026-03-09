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

## LLM (assistant)

Pour que le chat réponde réellement, configurer `LLM_API_KEY` ou `OPENAI_API_KEY` dans backend/.env (voir `backend/.env.example`). Optionnel : `LLM_BASE_URL`, `LLM_MODEL`. Détails : [ENV_SETUP.md](ENV_SETUP.md) section D.

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

### Exécution locale

**Prérequis :**

1. Backend lancé sur le port 8080 (ex. `./scripts/start-dev.sh` ou `cd backend && mvn spring-boot:run`)
2. Clé API : les tests lisent `HORAIN_API_KEY` depuis `backend/.env` (ou `VITE_API_KEY` / `HORAIN_API_KEY` en env). La clé doit correspondre à celle du backend pour éviter les 401.

```bash
cd frontend
npm run test:e2e
```

Playwright construit le frontend et le sert sur 4173. Les tests appellent le backend sur 8080 (seed, API projects/time-logs).

### CI (.github/workflows/deploy.yml)

À chaque push sur `main`, le job `test` s'exécute avant le déploiement :

1. **Tests backend :** `mvn test` (H2 en mémoire, pas de DB externe)
2. **Tests e2e :** Démarrage du backend (port 8080), build et serve du frontend (4173), puis `npm run test:e2e`

Le frontend est buildé avec `VITE_API_URL=http://localhost:8080` pour que les tests appelent le backend local. Le déploiement utilise les secrets (`VITE_API_URL` pointant vers Render) pour le build de production.

**Secret requis :** `OPENAI_API_KEY` (ou `LLM_API_KEY`). Les tests e2e envoient des messages à l'agent ; sans clé LLM, le backend utilise un placeholder et les tests échouent. Ajouter le secret dans Settings → Secrets and variables → Actions.

## Release

Versioning conforme à Maven : le code en développement porte une version SNAPSHOT (ex. `0.1.0-SNAPSHOT`). Chaque release produit une version sans suffixe puis prépare la prochaine SNAPSHOT.

Pour créer une release avec version sémantique et publication sur GitHub :

```bash
./scripts/release-version.sh --patch   # 0.1.0-SNAPSHOT → release 0.1.1, puis 0.1.2-SNAPSHOT
./scripts/release-version.sh --minor   # 0.1.0-SNAPSHOT → release 0.2.0, puis 0.2.1-SNAPSHOT
./scripts/release-version.sh --major   # 0.1.0-SNAPSHOT → release 1.0.0, puis 1.0.1-SNAPSHOT
```

Ou via npm : `npm run release -- --patch|--minor|--major`

**Prérequis :** working tree propre, GitHub CLI (`gh`) installé et authentifié.

**Étapes du script :** (1) vérification du working tree et de `gh`, tests backend et e2e, build frontend ; (2) phase release : extraction de la base (sans -SNAPSHOT), bump selon option, mise à jour des 3 fichiers, commit, tag, push ; (3) phase next dev : bump patch, ajout de -SNAPSHOT, commit, push. Le workflow GitHub crée la release avec un changelog auto-généré.

**Affichage UI :** En version SNAPSHOT, le header affiche aussi le short commit hash (ex. `v0.1.0-SNAPSHOT (a1b2c3d)`) pour distinguer les builds.

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
