# ADR-0003 : Déploiement via GitHub Actions

## Contexte

Le front et le backend doivent être déployés automatiquement à chaque push/tag.

## Décision

- **Front-end :** GitHub Pages, déployé via GitHub Actions sur push sur `main`
- **Backend :** Render, déployé via GitHub Actions (push ou webhook)
- **Tests e2e :** Exécutés en CI avant déploiement du front (comme [chrono-eps](https://github.com/plamarque/chrono-eps))

## Conséquences

- Un seul outil (GitHub Actions) pour la CI/CD.
- Les e2e bloquent le déploiement en cas d’échec.
- Pattern éprouvé (chrono-eps : build → test → e2e → upload artifact → deploy).
