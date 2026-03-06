# Render — Guide de création du Web Service

Copie-colle ces valeurs dans le formulaire Render.

---

## Champs du formulaire

### Source
- **Repository :** `patrice/horain` (déjà connecté)
- **Branch :** `main`

Render redéploie automatiquement à chaque push sur `main`. Aucune action GitHub supplémentaire n'est nécessaire pour le backend.

### Identification
- **Name :** `horain-api` (ou `horain`)

### Build & Run
- **Language :** `Docker` *(Java n'est pas proposé ; le Dockerfile build et exécute le backend)*
- **Root Directory :** `backend`
- **Build Command :** *(laisser vide)*
- **Start Command :** *(laisser vide)*

Render détecte le `Dockerfile` dans `backend/` et gère build + démarrage.

### Instance
- **Region :** Frankfurt (EU Central) — ou celle la plus proche de ton projet Supabase
- **Instance Type :** Free (pour tester)

---

## Variables d'environnement

Clique sur **Add Environment variable** pour chaque ligne. Remplace les valeurs Supabase par les tiennes (voir [ENV_SETUP.md](ENV_SETUP.md) section A).

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` | `postgres.TON_PROJECT_REF` |
| `SPRING_DATASOURCE_PASSWORD` | *(ton mot de passe Supabase)* |
| `HORAIN_API_KEY` | *(ex. `openssl rand -hex 32`)* — **pas** HEALTH_API_KEY |

**Optionnel** (pour l'agent LLM plus tard) :

| Key | Value |
|-----|-------|
| `OPENAI_API_KEY` | *(ta clé OpenAI sk-...)* |

---

## Ordre des étapes

1. Choisir **Language** = `Docker`
2. Remplir **Root Directory** = `backend`
3. Laisser **Build Command** et **Start Command** vides
4. Ajouter les variables d'environnement
5. Cliquer **Deploy Web Service**

---

## Après déploiement

Note l'URL du service (ex. `https://horain.onrender.com`). Utilise-la comme `VITE_API_URL` dans les secrets GitHub Actions pour le build frontend en production.
