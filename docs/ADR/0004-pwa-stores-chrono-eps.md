# ADR-0004 : PWA mobile-first et distribution stores inspirée de chrono-eps

## Contexte

L’application doit être disponible sur mobile (cible principale : Pixel 9a) et envisager une distribution via les stores (Play Store, App Store).

## Décision

- **PWA mobile-first** : interface pensée d’abord pour mobile ; Pixel 9a comme dispositif de référence
- **Distribution stores** : s’inspirer de [chrono-eps](https://github.com/plamarque/chrono-eps) :
  - **Android :** Trusted Web Activity (TWA) dans `android-twa/` (Gradle, twa-manifest.json)
  - **iOS :** Projet Xcode dans `ios/` avec Fastlane pour build et soumission App Store
  - **Workflows :** `release-stores.yml` (build et publication sur tag `v*`), `promote-stores.yml` si nécessaire

## Conséquences

- Une seule codebase web ; TWA et wrapper iOS chargent l’URL de production.
- Réutilisation des patterns de chrono-eps (deploy, release-stores, promote-stores).
- Nécessité de maintenir les projets android-twa et ios à jour avec l’URL et le manifest.
