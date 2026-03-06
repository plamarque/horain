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

## Tests e2e

- **Obligation :** Mettre en place et maintenir une suite de tests e2e dès le début du projet.
- **Outil :** Playwright.
- **Exécution :** Intégrée au pipeline CI avant déploiement.

## Publication sur les stores

Voir [docs/PUBLISHING_STORES.md](PUBLISHING_STORES.md) pour le guide complet de publication sur l'App Store et le Play Store (inspiré de [chrono-eps](https://github.com/plamarque/chrono-eps)).

## Contribution

- Lire SPEC, DOMAIN et ARCH avant de modifier le comportement ou la structure.
- **Code, commentaires et messages de commit** : toujours en anglais.
- Mettre à jour les docs normatifs quand le comportement ou l'architecture change.
- Garder PLAN et ISSUES factuels.
- Ajouter ou adapter les tests e2e pour les nouvelles fonctionnalités.
