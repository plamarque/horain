# Publication sur les stores

## Objet

Ce guide décrit comment publier Horain (PWA) sur l'Apple App Store et le Google Play Store, en s'appuyant sur PWABuilder. Référence : [ARCH.md](ARCH.md), [ADR 0004](ADR/0004-pwa-stores-chrono-eps.md).

Cette documentation est adaptée du guide [Chrono EPS](https://github.com/plamarque/chrono-eps) (`docs/PUBLISHING_STORES.md`).

## 1. Prérequis communs

- PWA déployée sur URL HTTPS publique : `https://<owner>.github.io/horain/` (remplacer `<owner>` par l'utilisateur ou l'organisation GitHub)
- Manifeste complet (nom, icônes 192/512, description, `start_url`, etc.) — configuré dans `vite.config.ts`
- Service worker valide
- Scores Lighthouse PWA acceptables

**Comptes développeur requis :**

| Store | Compte | Frais |
|-------|--------|-------|
| **Google Play** | [Google Play Console](https://play.google.com/console) | Frais unique (~25 USD) |
| **Apple** | [Apple Developer Program](https://developer.apple.com/programs/) | Abonnement annuel (99 USD) |

## 2. Validation avant packaging

Avant de générer les paquets :

1. Exécuter l'audit Lighthouse (onglet PWA) sur l'URL de production
2. Corriger les éventuels avertissements
3. S'assurer que l'app fonctionne en mode standalone
4. [Horain] L'app appelle un backend (Render) et Supabase ; vérifier que les appels API fonctionnent correctement en production

## 3. Packaging avec PWABuilder

Workflow PWABuilder :

1. Aller sur [PWABuilder](https://pwabuilder.com)
2. Saisir l'URL : `https://<owner>.github.io/horain/`
3. Cliquer sur *Next* pour afficher le rapport (scores, action items)
4. Cliquer sur *Package for Stores*
5. Pour chaque plateforme (Android, iOS), cliquer *Generate Package*
6. Fournir les métadonnées (nom, URL, icônes, etc.) — préremplies depuis le manifeste
7. Télécharger le paquet généré

## 4. Publication sur Google Play Store (Android — TWA)

PWABuilder génère un projet Android (Trusted Web Activity via Bubblewrap) ou un AAB prêt à uploader.

### 4.0 Génération du bundle Android (automatisée)

Le bundle AAB peut être généré en ligne de commande via [Bubblewrap](https://github.com/GoogleChromeLabs/bubblewrap).

**Configuration one-time** (à exécuter une seule fois) :

```bash
npx @bubblewrap/cli init \
  --manifest="https://<owner>.github.io/horain/manifest.webmanifest" \
  --directory="android-twa"
```

Répondre aux questions (package name, domain, etc.). Une clé de signature est générée dans `android-twa/`. **Sauvegarder le keystore et ses mots de passe.** Le keystore est ignoré par git (`.gitignore`).

**Génération du AAB :**

Créer un fichier `.env` à la racine du projet (non commité) avec les mots de passe :

```bash
cp .env.example .env
# Éditer .env et renseigner BUBBLEWRAP_KEYSTORE_PASSWORD et BUBBLEWRAP_KEY_PASSWORD
```

Puis lancer :

```bash
npm run android:bundle
```

Le script charge automatiquement `.env`. Le AAB est produit dans `dist/horain-android.aab`.

**Intégration à la release :** exécuter `./scripts/release-version.sh --patch` déclenche le workflow CI qui génère l'AAB et le distribue sur Play Store (piste internal).

### 4.1 Digital Asset Links (apparence standalone)

Sans ce fichier, l'app Android ouvre le site dans Custom Tabs avec la **barre du navigateur visible**. Les Digital Asset Links permettent le mode **standalone** (plein écran, sans barre d'adresse).

Le fichier doit être accessible à : `https://<owner>.github.io/.well-known/assetlinks.json`

**Étapes :**

1. **Récupérer le fingerprint SHA-256** dans Play Console :
   - *Paramètres* → *Intégrité de l'application* (ou *App signing*)
   - Copier le **SHA-256 du certificat de signature de l'application** (clé gérée par Google, pas la clé de téléversement)

2. **Remplacer dans** `public/.well-known/assetlinks.json` :
   ```json
   "sha256_cert_fingerprints": ["XX:XX:XX:XX:..."]
   ```

3. **Héberger à la racine du domaine** — le site Horain est en `<owner>.github.io/horain/`, donc `/.well-known/` doit être servi par `<owner>.github.io` :
   - Créer un dépôt **`<owner>/<owner>.github.io`** (s'il n'existe pas)
   - Y ajouter le dossier `.well-known/` avec `assetlinks.json`
   - Le fichier sera servi à `https://<owner>.github.io/.well-known/assetlinks.json`

4. **Vérifier** : https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://<owner>.github.io&relation=delegate_permission/common.handle_all_urls

### 4.2 Clé de signature

Générer une clé de signature pour l'AAB si PWABuilder ne le fait pas automatiquement. Conserver la clé en lieu sûr pour les mises à jour futures.

### 4.3 Google Play Console

1. Créer une application dans [Google Play Console](https://play.google.com/console)
2. Remplir la fiche store : description, captures d'écran, politique de confidentialité, catégorie
3. Uploader l'AAB dans *Production* ou *Testing*
4. Soumettre pour révision

### 4.4 Ressources Android

- [PWABuilder Android docs](https://docs.pwabuilder.com/#/builder/android)
- [Trusted Web Activity Quick Start](https://developer.chrome.com/docs/android/trusted-web-activity/quick-start)
- [Digital Asset Links](https://developers.google.com/digital-asset-links/v1/getting-started)

## 5. Publication sur Apple App Store (iOS)

PWABuilder génère un projet Xcode (Swift + WebKit) à compiler.

### 5.1 Prérequis

- Mac avec Xcode installé
- Compte Apple Developer actif
- Runtime iOS installé (Xcode → Settings → Platforms → iOS)
- Projet iOS dans `ios/` (généré via PWABuilder)

### 5.2 Ouvrir le projet

```bash
open ios/Horain.xcworkspace
```

**Important** : ouvrir le `.xcworkspace`, pas le `.xcodeproj` (le projet utilise CocoaPods).

### 5.3 Générer les screenshots

**Génération automatisée (recommandée) :** `npm run screenshots` produit tous les écrans (iOS + Android) dans les résolutions requises. Voir `public/screenshots/README.md` si présent.

**Génération manuelle** (simulateur Xcode) :

#### 5.3.1 Screenshots iPhone

1. Dans la barre d'outils Xcode, cliquer sur le sélecteur de destination.
2. Choisir **iPhone 17 Pro Max** (ou un simulateur iPhone équivalent).
3. Lancer l'app : **Cmd+R**.
4. Attendre le chargement de la PWA dans le simulateur.
5. Naviguer dans l'app pour afficher les écrans à capturer.
6. **Capture** : **Cmd+S** dans le simulateur (ou clic droit → Save Screen).
7. Les PNG sont sauvegardés sur le Bureau par défaut.
8. Déplacer les captures dans `public/screenshots/ios/iphone/` avec des noms explicites.

**Tailles App Store** : iPhone 6.5" = 1284×2778 px (portrait).

#### 5.3.2 Screenshots iPad

1. Arrêter le simulateur : **Cmd+.**.
2. Choisir **iPad Pro 13-inch** ou **iPad Air 13-inch**.
3. Lancer : **Cmd+R**.
4. Mettre le simulateur en **paysage** si nécessaire.
5. Capturer les mêmes écrans avec **Cmd+S**.
6. Déplacer vers `public/screenshots/ios/ipad/`.

**Tailles App Store** : iPad 13" = 2732×2048 px (paysage).

### 5.4 Créer l'archive (build)

1. Sélectionner **Any iOS Device (arm64)** comme destination.
2. Menu **Product → Archive**.
3. Si une fenêtre demande un mot de passe : saisir le mot de passe de session macOS.
4. Si erreur de signature : vérifier **Signing & Capabilities**, cocher **Automatically manage signing**, sélectionner le **Team**.
5. Attendre la fin de la compilation.
6. L'**Organizer** s'ouvre automatiquement.

### 5.5 Uploader vers App Store Connect

1. Dans **Organizer**, sélectionner l'archive créée.
2. Cliquer **Distribute App**.
3. Choisir **App Store Connect** → Next.
4. Choisir **Upload** → Next.
5. Accepter les options par défaut → Next.
6. Sélectionner le profil de distribution → Next.
7. Cliquer **Upload**.
8. Attendre la fin du traitement.

### 5.6 Compléter App Store Connect

1. Aller sur [App Store Connect](https://appstoreconnect.apple.com/).
2. Sélectionner l'app **Horain** → Version iOS 1.0 (ou la version en cours).
3. Remplir le formulaire avec le contenu de l'[Annexe A](#annexe-a-contenu-app-store-connect).
4. Téléverser les screenshots depuis `public/screenshots/ios/`.
5. Sélectionner le build uploadé.
6. Renseigner les informations de contact pour App Review.
7. Soumettre pour révision.

**Structure des screenshots :**

```
public/screenshots/
├── ios/
│   ├── iphone/
│   └── ipad/
└── android/
    ├── smartphone/
    └── tablet/
```

### 5.7 Notes importantes

Apple peut refuser les apps qui ressemblent à de simples « sites web dans une frame ». Horain fournit une vraie valeur (backend IA, données Supabase, PWA) — documenter les fonctionnalités distinctives pour la revue.

### 5.8 Ressources iOS

- [PWABuilder iOS docs](https://docs.pwabuilder.com/#/builder/app-store)
- [Blog post : Publish your PWA to the iOS App Store](https://blog.pwabuilder.com/posts/publish-your-pwa-to-the-ios-app-store)
- [App Store Screenshot Specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/)

## 6. Pièces à préparer pour les deux stores

| Élément | Description |
|---------|-------------|
| Captures d'écran | Plusieurs tailles (téléphone, tablette) — cf. [Annexe B](#annexe-b-spécifications-des-screenshots-référence) |
| Icône 512×512 | `public/pwa-512x512.png` — régénérer le 192×192 avec `npm run icons` |
| Image de présentation (1024×500) | Générer via `public/store-feature-graphic.html` : ouvrir dans un navigateur, télécharger l'image PNG |
| Description courte | À rédiger selon le produit |
| Description longue | Détail des fonctionnalités |
| Politique de confidentialité | URL obligatoire. Horain utilise un backend (Render) et Supabase ; la politique doit couvrir les données collectées, stockées et les appels API. |
| Catégorie | À choisir selon le produit (ex. Productivité) |

## 7. Limitations et points d'attention (Horain)

| Point | Détail |
|-------|--------|
| **GitHub Pages + Digital Asset Links** | `/.well-known/assetlinks.json` doit être servi à la racine du domaine. Si le site est sur `owner.github.io/horain/`, créer un dépôt `owner.github.io` pour héberger `/.well-known/`. |
| **base path** | `base: '/horain/'` dans Vite — vérifier que `start_url` et les chemins sont corrects dans le manifeste pour le packaging. |
| **Backend et données** | Horain a un backend (Spring AI sur Render) et Supabase. La politique de confidentialité doit documenter les flux de données (API, base, fournisseurs IA). |
| **Precache index.html** | Exclure `index.html` du precache Workbox pour que les apps TWA/iOS affichent la bonne version après mise à jour store sans refresh manuel. |

## 8. Pipeline CI/CD

Le workflow `.github/workflows/release-stores.yml` s'exécute à chaque push de tag `v*` et automatise la release complète. Structure inspirée de [chrono-eps](https://github.com/plamarque/chrono-eps).

### 8.1 Secrets GitHub requis

| Secret | Description |
|--------|-------------|
| `PLAY_STORE_SERVICE_ACCOUNT` | JSON du Service Account (Google Cloud) ayant accès à l'API Play Console |
| `ANDROID_KEYSTORE_BASE64` | Keystore encodé en base64 (`base64 -i android-twa/android.keystore`) |
| `BUBBLEWRAP_KEYSTORE_PASSWORD` | Mot de passe du keystore |
| `BUBBLEWRAP_KEY_PASSWORD` | Mot de passe de la clé |
| `APPSTORE_ISSUER_ID` | Issuer ID (App Store Connect → Intégrations) |
| `APPSTORE_KEY_ID` | Key ID de la clé API |
| `APPSTORE_API_PRIVATE_KEY` | Contenu du fichier .p8 (clé API) |
| `MATCH_PASSWORD` | Mot de passe pour décrypter les certificats Match |
| `MATCH_GIT_URL` | URL HTTPS du dépôt privé contenant les certificats (ex. `https://github.com/user/certificates`) |
| `MATCH_GIT_BASIC_AUTHORIZATION` | Base64 de `username:token` ou `x-access-token:TOKEN` pour cloner le dépôt Match |

### 8.2 Configuration Fastlane Match (iOS)

Avant la première exécution du job iOS :

1. Créer un dépôt Git privé (ex. `horain-certificates`)
2. Dans `ios/` : `bundle install` puis `bundle exec fastlane match appstore`
3. Suivre les invites (git_url, mot de passe) pour stocker certificat et provisioning profile
4. Ajouter les secrets `MATCH_*` dans GitHub

### 8.3 Flux complet

```
Release : ./scripts/release-version.sh --patch
    → push main + tag v*
    → workflow : create-release → build-android | build-ios (parallèle)
    → Release GitHub + Play Store internal + TestFlight + binaires attachés

Promote : ./scripts/promote-to-stores.sh v0.1.2
    → workflow promote-stores.yml (déclenché manuellement)
    → Play Store production + soumission App Store pour review
```

**Jobs du workflow release-stores.yml :**
- `create-release` : crée la release GitHub avec changelog
- `build-android` : build AAB via Bubblewrap, upload Play Store (internal)
- `build-ios` : build IPA via Fastlane, upload TestFlight, attache à la release

### 8.4 Promotion vers la production

| Action | Cible | Commande |
|--------|-------|----------|
| **Release** | Testeurs (internal, TestFlight) | `./scripts/release-version.sh --patch` |
| **Promote** | Production (stores publics) | `./scripts/promote-to-stores.sh v0.1.2` ou `latest` |

Une revue Apple et Google est obligatoire à chaque mise à jour en production ; les délais sont variables (souvent 24–48 h).

### 8.5 Détails d'implémentation (référence Chrono EPS)

**Android (Bubblewrap) :**
- En CI, `yes y` est pipé pour répondre aux prompts. Utiliser `update ... < /dev/null` pour isoler la commande `update`.
- **versionCode unique** : `GITHUB_RUN_NUMBER` peut être intégré pour les ré-uploads.

**iOS (Fastlane) :**
- **CFBundleShortVersionString** : synchroniser avec le tag (ex. v0.3.1 → 0.3.1).
- **Build number unique** : `GITHUB_RUN_NUMBER` comme CFBundleVersion.

**PWA :**
- Exclure `index.html` du precache Workbox pour que les apps TWA/iOS affichent la bonne version après mise à jour.

## 9. Dépannage

### Erreur iOS : « CFBundleShortVersionString must contain a higher version than previously approved »

Apple considère « 1 » et « 1.0.0 » comme **équivalents**. Utiliser `release-version.sh --patch` pour incrémenter : 1.0.0 → 1.0.1.

### Erreur Android : « release notes too long (max: 500) »

Les notes de mise à jour sont limitées à 500 caractères. Tronquer le changelog dans le workflow si nécessaire.

## 10. Liens et références

- [PWABuilder](https://pwabuilder.com/)
- [PWABuilder docs — Android](https://docs.pwabuilder.com/#/builder/android)
- [PWABuilder docs — App Store](https://docs.pwabuilder.com/#/builder/app-store)
- [web.dev — PWAs in app stores](https://web.dev/articles/pwas-in-app-stores)
- [Google Play Console](https://play.google.com/console)
- [App Store Connect](https://appstoreconnect.apple.com/)
- [Chrono EPS — PUBLISHING_STORES.md](https://github.com/plamarque/chrono-eps/blob/main/docs/PUBLISHING_STORES.md) (document original)

---

## Annexe A — Contenu App Store Connect

Template à compléter pour Horain. Remplacer les placeholders par le contenu réel.

### Promotional Text (max 170 caractères)

[À rédiger : résumé accrocheur du produit.]

### Description

[À rédiger : description détaillée des fonctionnalités, public cible, valeur ajoutée.]

### Keywords (max 100 caractères, virgules, sans espaces)

[Ex. : mot1,mot2,mot3]

### URLs

| Champ | Valeur |
|-------|--------|
| **Support URL** | https://github.com/\<owner\>/horain/issues |
| **Marketing URL** (optionnel) | https://github.com/\<owner\>/horain |
| **Politique de confidentialité** | https://\<owner\>.github.io/horain/privacy.html |

### Copyright

© 2026 [Nom du titulaire des droits]

### App Review Information

**Sign-in required** : Cocher si l'app exige une authentification ; sinon laisser décoché.

**Notes (pour faciliter la revue)** : [Décrire brièvement l'app, son usage, les données (local/backend). Pour Horain : PWA avec backend Spring AI et Supabase, appels IA côté serveur.]

**Contact Information** : First Name, Last Name, Phone Number, Email.

### Checklist

- [ ] Promotional Text
- [ ] Description
- [ ] Keywords
- [ ] Support URL
- [ ] Marketing URL (optionnel)
- [ ] Copyright
- [ ] Screenshots iPhone (1284×2778)
- [ ] Screenshots iPad (2732×2048 paysage)
- [ ] Build uploadé
- [ ] App Review contact + Notes
- [ ] Politique de confidentialité

---

## Annexe B — Spécifications des screenshots (référence)

### Google Play Store (Android)

Format : PNG ou JPEG, max 8 Mo par image. Ratio 16:9 ou 9:16.

| Cible | Côté min | Côté max |
|-------|----------|----------|
| **Téléphones** | 320 px | 3840 px |
| **Tablettes 7"** | 320 px | 3840 px |
| **Tablettes 10"** | 1080 px | 7680 px |

### Apple App Store (iOS)

| Cible | Dimensions | Orientation |
|-------|------------|-------------|
| **iPhone 6.5"** | 1284 × 2778 px | Portrait |
| **iPad 13"** | 2732 × 2048 px | Paysage |
