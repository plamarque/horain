# ADR-0005 : Suite de tests e2e dès le début avec Playwright

## Contexte

Les régressions doivent être détectées rapidement. Les tests e2e valident le comportement réel de l’application de bout en bout.

## Décision

- **Outil :** Playwright
- **Règle :** La suite de tests e2e doit être créée et maintenue dès le début du projet
- **Exécution :** Dans le pipeline CI avant chaque déploiement (comme chrono-eps)

## Conséquences

- Les nouvelles fonctionnalités doivent s’accompagner de tests e2e ou de mises à jour des tests existants.
- Le déploiement sur GitHub Pages est bloqué si les tests e2e échouent.
- Alignement avec le pattern de chrono-eps (install Playwright chromium, npm run test:e2e).
