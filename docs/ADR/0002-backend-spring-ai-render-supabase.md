# ADR-0002 : Backend Spring AI sur Render, base Supabase

## Contexte

Les appels IA doivent être réalisés côté serveur pour ne pas exposer les clés d’API. Une base de données hébergée et managée est souhaitée.

## Décision

- **Backend :** Java Spring AI
- **Hébergement :** Render
- **Base de données :** Supabase (PostgreSQL)

## Conséquences

- Les clés d’API IA restent sur le serveur (variables d’environnement Render).
- Supabase fournit PostgreSQL, auth et API REST optionnels.
- Spring AI simplifie l’intégration avec les fournisseurs IA.
