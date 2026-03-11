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
2. **Backend + seed :** Démarrage du backend (port 8080), seed via POST /dev/seed
3. **Evals Promptfoo :** `npm run eval:deterministic` (suite déterministe uniquement ; pas de juge Mistral)
4. **Tests e2e :** Build et serve du frontend (4173), puis `npm run test:e2e`

Les evals **scorés** (LLM-as-judge) sont exécutés uniquement lors d'une **release** (workflow `.github/workflows/evals-scored.yml` sur événement release ou tag `v*`). Secret requis : `PROMPTFOO_JUDGE_MISTRAL_API_KEY`.

Le frontend est buildé avec `VITE_API_URL=http://localhost:8080` pour que les tests appelent le backend local. Le déploiement utilise les secrets (`VITE_API_URL` pointant vers Cloud Run) pour le build de production.

**Secret requis :** `OPENAI_API_KEY` (ou `LLM_API_KEY`). Les tests e2e envoient des messages à l'agent ; sans clé LLM, le backend utilise un placeholder et les tests échouent. Ajouter le secret dans Settings → Secrets and variables → Actions.

### Release locale (scripts/release-version.sh)

Le script démarre le backend automatiquement avant les e2e (comme en CI), attend `/health` puis lance Playwright. Pour que les tests passant par le chat (assistant) réussissent, configurer `LLM_API_KEY` ou `OPENAI_API_KEY` dans `backend/.env` (voir section LLM ci-dessus).

## Evals Promptfoo

Les evals Promptfoo évaluent le comportement de l'agent conversationnel (POST /chat/message) via des assertions sur les réponses.

Pour les **règles de maintenance** (quand ajouter, modifier ou supprimer des tests), voir [docs/EVALS.md](EVALS.md).

**Prérequis :**

1. Backend sur 8080 (comme pour e2e)
2. `HORAIN_API_KEY` doit correspondre à celle du backend (défaut : `HORAIN_DEV_KEY`)
3. `OPENAI_API_KEY` ou `LLM_API_KEY` dans `backend/.env` pour des réponses LLM réelles

**Exécution :**

```bash
# Script complet : démarre le backend si besoin, seed, lance les evals
./scripts/run-promptfoo-eval.sh

# Déterministe uniquement (pas de juge Mistral, adapté à la CI)
./scripts/run-promptfoo-eval.sh --deterministic-only

# Depuis la racine du dépôt (npm run a pour CWD la racine) :
npm run eval:deterministic

# Suite complète avec evals scorés (juge Mistral, --max-concurrency 1 pour 6 req/min)
./scripts/run-promptfoo-eval.sh --scored

# Ou manuellement (backend déjà lancé) — exporter la clé pour éviter 401 Unauthorized
export HORAIN_API_KEY="${HORAIN_API_KEY:-HORAIN_DEV_KEY}"   # ou depuis backend/.env
cd promptfoo && npx promptfoo eval
npm run eval:deterministic   # déterministe seulement
```

**Cibler un test (ou un sous-ensemble) :** le script transmet tous les arguments à `promptfoo eval`. Utiliser `--filter-pattern` (regex sur le champ `description` des tests) pour ne lancer que certains tests :

```bash
# Un seul test (ex. description "Log time direct - 20 minutes on Horain")
./scripts/run-promptfoo-eval.sh --filter-pattern "Log time direct"

# Plusieurs tests dont la description matche (ex. tous les analytics)
./scripts/run-promptfoo-eval.sh --filter-pattern "analytics|taux"

# Combiné avec déterministe ou scoré
./scripts/run-promptfoo-eval.sh --deterministic-only --filter-pattern "Smoke"
```

Les descriptions sont dans `promptfooconfig.yaml` et dans les YAML sous `promptfoo/tests/`.

**Erreur « No configuration file found at promptfoo/promptfooconfig.deterministic.yaml » :** le chemin est résolu par rapport au répertoire courant. Depuis le dépôt, lancer `npm run eval:deterministic` (CWD = racine) ou `./scripts/run-promptfoo-eval.sh --deterministic-only`. Si vous êtes dans `promptfoo/`, utiliser `-c promptfooconfig.deterministic.yaml` (sans préfixe `promptfoo/`).

**Rapport et relance des échecs :**
- Générer un rapport HTML : `./scripts/run-promptfoo-eval.sh --deterministic-only --output report.html` (fichier créé dans `promptfoo/`).
- Relancer uniquement les tests qui ont échoué lors d’un run précédent : il faut d’abord exporter en JSON (ex. `--output results.json`), puis `--filter-failing-only results.json` (ou `--filter-failing results.json`). Les options `--filter-failing` et `--filter-failing-only` exigent donc un chemin vers un fichier de résultats ou un eval ID.

### Evals scorés (LLM-as-judge)

Les tests sous `promptfoo/tests/scored/` utilisent Mistral comme juge pour noter la qualité des réponses (résumés hebdomadaires, questions ouvertes, robustesse au langage vague, pertinence conversationnelle, calculs de taux d'occupation). Les questions de type « taux » sont couvertes en déterministe par `analytics-taux.yaml` et en scoré par `rate-calculation.yaml`. Les variables sont chargées depuis `promptfoo/.env` par le script ; on peut aussi les exporter avant d'appeler le script.

| Variable | Rôle |
|----------|------|
| `PROMPTFOO_JUDGE_MISTRAL_API_KEY` | Clé API Mistral pour le juge |
| `PROMPTFOO_JUDGE_MODEL` | Modèle Mistral (défaut : `mistral-small-latest`) |

Copier `promptfoo/.env.example` en `promptfoo/.env` et renseigner la clé. En CI (release), le secret `PROMPTFOO_JUDGE_MISTRAL_API_KEY` est utilisé par le workflow `evals-scored.yml`. En cas d'échec, le workflow affiche un résumé des tests en échec dans les logs et publie l'artifact **promptfoo-eval-report** (JSON + HTML complets) ; télécharger l'artifact depuis la page de la run GitHub Actions pour analyser le rapport en détail.

Voir [promptfoo/README.md](../promptfoo/README.md) pour la configuration et la structure des tests.

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
