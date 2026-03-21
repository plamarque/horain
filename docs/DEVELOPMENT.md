# Development

## Prérequis

- Node 20+
- npm ou pnpm
- JDK 21+ (cible JVM du backend Kotlin / Spring Boot)
- Maven (backend Kotlin uniquement ; pas de Gradle dans ce dépôt)

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

**Raisonnement (modèles o1, o3, o4-mini) :** Avec un modèle à raisonnement (ex. `LLM_MODEL=o4-mini`), le client Responses API est choisi automatiquement ; aucun `llm.client` n’est requis. En cas d’échec (400/404/422), le backend bascule une seule fois sur Chat Completions et conserve ce choix pour les appels suivants. Pour forcer le client : `llm.client=openai-responses` (Responses uniquement) ou `llm.client=openai-compatible` (Completions uniquement).

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

**Pour faire passer les e2e :** à la racine du dépôt, lancer `./scripts/run-tests.sh e2e`. Le script démarre le backend, build le frontend avec `VITE_API_URL=http://localhost:8080`, puis exécute Playwright. Lancer uniquement `npm run test:e2e` depuis `frontend/` sans backend ni build adapté fait échouer la plupart des tests.

**Prérequis (si vous lancez les tests à la main) :**

1. Backend lancé sur le port 8080 (ex. `./scripts/start-dev.sh` ou `cd backend && mvn spring-boot:run`)
2. Clé API : les tests lisent `HORAIN_API_KEY` depuis `backend/.env` (ou `VITE_API_KEY` / `HORAIN_API_KEY` en env). La clé doit correspondre à celle du backend pour éviter les 401.

```bash
cd frontend
npm run test:e2e
```

Playwright sert le `dist` existant sur 4173 (il ne rebuild pas). L’app doit avoir été buildée avec `VITE_API_URL=http://localhost:8080` pour appeler le backend sur 8080 ; sinon les appels API échouent et les tests tombent (ex. « Dernières activités » absent, pas de bulle assistant).

**Recommandé — Script tout-en-un :** Depuis la racine du dépôt, `./scripts/run-tests.sh e2e` démarre le backend si besoin, build le frontend avec la bonne URL, sert sur 4173, puis lance Playwright. À utiliser en priorité pour éviter les échecs liés à l’env.

**Script tout-en-un (détail) :** `./scripts/run-tests.sh` démarre le backend si besoin, attend qu’il soit prêt, lance les e2e puis arrête le backend. Si tous les tests passent, le script quitte avec le code 0. Un message Maven « BUILD FAILURE » peut apparaître à la fin lors de l’arrêt du backend ; c’est attendu et peut être ignoré.

### CI (.github/workflows/deploy.yml)

À chaque push sur `main`, le job `test` s'exécute avant le déploiement :

1. **Tests backend :** `mvn test` (H2 en mémoire, pas de DB externe) — **toujours** exécutés
2. **E2e (sauf bump de version seul) :** Si le push ne modifie **que** `package.json`, `frontend/package.json` et/ou `backend/pom.xml` (ex. commit « prepare next dev » après release), les étapes Playwright + e2e sont **ignorées** pour gagner du temps ; le déploiement repose sur la suite complète déjà passée sur le commit précédent.
3. **Sinon :** build frontend pour e2e (`npm run build` avec `VITE_API_URL=http://localhost:8080`), backend + seed (`POST /dev/seed`), `serve` sur 4173, puis `npm run test:e2e`

Les evals **scorés** (LLM-as-judge) s'exécutent **une fois** lorsqu'une **GitHub Release est publiée** (workflow `.github/workflows/evals-scored.yml`, événement `release: published`, plus `workflow_dispatch`). Secret requis : `PROMPTFOO_JUDGE_MISTRAL_API_KEY`.

**Release (tag v\*):** Le push d'un tag déclenche `.github/workflows/release.yml`, qui crée la **GitHub Release** et le changelog. Les tests complets (backend + e2e) sont assurés par **deploy.yml** sur le même commit lorsque `main` est poussé (voir `scripts/release-version.sh`).

Le frontend est buildé avec `VITE_API_URL=http://localhost:8080` pour que les tests appelent le backend local. Le déploiement utilise les secrets (`VITE_API_URL` pointant vers Cloud Run) pour le build de production.

**Ordre prod :** après les tests, le backend (Cloud Run) et le build front tournent en parallèle ; la **publication GitHub Pages** ne s’exécute qu’une fois le déploiement Cloud Run réussi pour ce commit. Si le **build front** ou le **déploiement Pages** échoue alors que le backend a bien été déployé, le workflow **remet le trafic** Cloud Run sur la révision qui servait avant ce run (évite une API neuve avec une UI encore à l’ancienne version).

**Secret requis :** `OPENAI_API_KEY` (ou `LLM_API_KEY`). Les tests e2e envoient des messages à l'agent ; sans clé LLM, le backend utilise un placeholder et les tests échouent. Ajouter le secret dans Settings → Secrets and variables → Actions.

### Release locale (scripts/release-version.sh)

Par défaut, le script démarre le backend avant les e2e, attend `/health` puis lance Playwright. Pour que les tests passant par le chat (assistant) réussissent, configurer `LLM_API_KEY` ou `OPENAI_API_KEY` dans `backend/.env` (voir section LLM ci-dessus).

**Options :** `--fast` — `mvn test` + `npm run build` seulement (pas d’e2e locale ; l’e2e tourne sur le push `main` dans CI). `--skip-tests` — aucun test ni build local avant le bump (rare ; la CI reste le garde-fou).

## Evals Promptfoo

Les evals Promptfoo évaluent le comportement de l'agent conversationnel (POST /chat/message) via des assertions sur les réponses.

Pour les **règles de maintenance** (quand ajouter, modifier ou supprimer des tests), voir [docs/EVALS.md](EVALS.md).

**Prérequis :**

1. Backend sur 8080 (comme pour e2e)
2. `HORAIN_API_KEY` doit correspondre à celle du backend (défaut : `HORAIN_DEV_KEY`)
3. `OPENAI_API_KEY` ou `LLM_API_KEY` dans `backend/.env` pour des réponses LLM réelles

**Exécution :**

```bash
# Script complet : démarre le backend si besoin, reset+seed au démarrage (état propre), lance les evals, puis teardown (reset+seed en sortie) pour ne pas laisser de données de test en base
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

## Export eval candidates

Pour alimenter la boucle d’amélioration et les evals (voir [EVALS.md](EVALS.md) section 9), un script exporte les **candidats à l’éval** : turns avec feedback 👎 ou avec statut d’erreur (tool_error, empty_result, max_iterations). La sortie est un fichier **JSONL** (une ligne = un objet par tour) avec notamment : `source_turn_id`, `conversation_id`, `user_message`, `assistant_message`, `tool_calls`, `feedback`, `feedback_reason`, `feedback_comment`, `system_prompt_version`, `expected_behavior` (vide à remplir au triage), `eval_family`, `assertion_strategy`.

**Champ `tool_calls` :** Liste des appels d’outils exécutés pour ce tour. Chaque élément est un objet `{ "name", "arguments", "result" }`. Les erreurs renvoyées par les outils sont dans `result` (souvent un JSON avec clé `"error"`, ex. `{"error": "Cannot delete project: it has 38 time log entries..."}`). Ce champ permet au triage d’identifier précisément quel outil a échoué et avec quel message, comme dans la trace affichée sous la bulle en prod.

Le script appelle `GET /admin/export-eval-candidates` sur l’instance **prod**. Il ne touche pas à la base locale ni au `.env` du backend.

**Prérequis :** `EVAL_CANDIDATES_ENDPOINT` (URL complète de l’endpoint, ex. `https://votre-prod.run.app/admin/export-eval-candidates`) et `HORAIN_API_KEY` (clé API Bearer).

**Commande :**

```bash
EVAL_CANDIDATES_ENDPOINT=https://votre-prod.run.app/admin/export-eval-candidates HORAIN_API_KEY=votre-cle ./scripts/export-eval-candidates.sh
```

Par défaut le fichier est écrit dans `scripts/out/eval-candidates.jsonl`. Autre sortie : `OUT_FILE=/chemin/vers/sortie.jsonl ./scripts/export-eval-candidates.sh`.

**Triage :** Ouvrir le JSONL, remplir `expected_behavior`, `eval_family`, etc. pour les cas à promouvoir, puis ajouter manuellement les cas retenus dans `promptfoo/tests/` (ou utiliser un script de promotion si vous en créez un).

## Evals Promptfoo — cibler un test

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

**Erreur 429 (rate limit OpenAI) en CI :** le backend retente automatiquement les appels OpenAI en cas de 429 (jusqu'à 5 fois, avec délai indiqué par l'API ou 2 s par défaut). Le script utilise `--max-concurrency 2` pour les evals déterministes afin de limiter le pic de requêtes. En cas de limite TPM stricte : `./scripts/run-promptfoo-eval.sh --deterministic-only --max-concurrency 1`.

**Rapport et relance des échecs :**
- Chaque run (via `run-tests.sh` en mode `deterministic`, `scored` ou `promptfoo`, ou via `run-promptfoo-eval.sh`) écrit les résultats dans **`promptfoo/output/eval-results.json`** et **`promptfoo/output/eval-results.html`**. L'eval ID affiché en console correspond à ce fichier après le run.
- Relancer uniquement les tests qui ont échoué lors d’un run précédent : il faut d’abord exporter en JSON (ex. `--output output/eval-results.json`), puis `--filter-failing-only output/eval-results.json` (ou `--filter-failing output/eval-results.json`). Les options `--filter-failing` et `--filter-failing-only` exigent un chemin vers un fichier de résultats ou un eval ID.

### Evals scorés (LLM-as-judge)

Les tests sous `promptfoo/tests/scored/` utilisent Mistral comme juge pour noter la qualité des réponses (résumés hebdomadaires, questions ouvertes, robustesse au langage vague, pertinence conversationnelle, calculs de taux d'occupation). Les questions de type « taux » sont couvertes en déterministe par `analytics-taux.yaml` et en scoré par `rate-calculation.yaml`. Les variables sont chargées depuis `promptfoo/.env` par le script ; on peut aussi les exporter avant d'appeler le script.

| Variable | Rôle |
|----------|------|
| `PROMPTFOO_JUDGE_MISTRAL_API_KEY` | Clé API Mistral pour le juge |
| `PROMPTFOO_JUDGE_MODEL` | Modèle Mistral (défaut : `mistral-small-latest`) |

Copier `promptfoo/.env.example` en `promptfoo/.env` et renseigner la clé. Lorsqu’une **GitHub Release est publiée**, le secret `PROMPTFOO_JUDGE_MISTRAL_API_KEY` est utilisé par le workflow `evals-scored.yml` (également lançable à la main via `workflow_dispatch`). En cas d'échec, le workflow affiche un résumé des tests en échec dans les logs et publie l'artifact **promptfoo-eval-report** (JSON + HTML complets) ; télécharger l'artifact depuis la page de la run GitHub Actions pour analyser le rapport en détail.

Voir [promptfoo/README.md](../promptfoo/README.md) pour la configuration et la structure des tests.

## Release

Versioning conforme à Maven : le code en développement porte une version SNAPSHOT (ex. `0.1.0-SNAPSHOT`). Chaque release produit une version sans suffixe puis prépare la prochaine SNAPSHOT.

Pour créer une release avec version sémantique et publication sur GitHub :

```bash
./scripts/release-version.sh --patch   # 0.1.0-SNAPSHOT → release 0.1.1, puis 0.1.2-SNAPSHOT
./scripts/release-version.sh --minor   # 0.1.0-SNAPSHOT → release 0.2.0, puis 0.2.1-SNAPSHOT
./scripts/release-version.sh --major   # 0.1.0-SNAPSHOT → release 1.0.0, puis 1.0.1-SNAPSHOT
./scripts/release-version.sh --patch --fast   # idem, sans e2e locale
./scripts/release-version.sh --patch --skip-tests   # idem, sans aucun test local (déconseillé sauf urgence)
```

Ou via npm : `npm run release -- --patch|--minor|--major` (ajouter `-- --fast` ou `-- --skip-tests` si besoin).

**Prérequis :** working tree propre, GitHub CLI (`gh`) installé et authentifié.

**Étapes du script :** (1) vérification du working tree et de `gh`, puis par défaut tests backend, e2e et build frontend (`--fast` / `--skip-tests` pour raccourcir) ; (2) phase release : extraction de la base (sans -SNAPSHOT), bump selon option, mise à jour des 3 fichiers, commit, tag, push ; (3) phase next dev : bump patch, ajout de -SNAPSHOT, commit, push. Le workflow `release.yml` crée la GitHub Release avec un changelog auto-généré ; `deploy.yml` exécute la suite de tests sur chaque push `main`.

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
