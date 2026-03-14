# Prompt à coller à l’agent Horain : enregistrer mes activités facturables

Copie-colle le bloc ci-dessous dans la conversation avec l’agent Horain pour qu’il enregistre ou mette à jour les types d’activité en base via les outils MCP (create_activity_type / update_activity_type). Les TJM sont en euros ; l’agent doit les convertir en centimes pour dailyRateCents.

---

**Instructions pour l’agent :**

Enregistre en base mes types d’activité facturables (activity types) en utilisant les outils MCP. Fais d’abord un `list_activity_types` pour voir ce qui existe déjà. Pour chaque type ci-dessous : s’il existe, mets à jour le label, le TJM (dailyRateCents) et la description ; sinon crée-le avec `create_activity_type`. Code en majuscules, court (ex. PROJ, PROD). TJM en euros → dailyRateCents (ex. 600 € → 60000).

**Jeu d’activités (réparti de façon cohérente à partir du seed actuel) :**

| Code   | Label                        | TJM (€) | Description (détection) |
|--------|------------------------------|---------|--------------------------|
| DEV    | Développement                | 600     | Conception, développement, test, documentation, ingénierie logicielle, déploiement et packaging. Synonymes : dev, dev logiciel. |
| PROJ   | Gestion de projet            | 300     | Rendez-vous, coordination, administratif, suivi. Synonymes : PM, coordination projet. |
| PROD   | Gestion de produit logiciel  | 500     | Coordination des évolutions logicielles, priorisation des besoins, suivi des développements. |
| MARK   | Marketing                    | 700     | Gestion de communauté, production de contenu, médias sociaux, SEO. |
| PROSPECT | Prospection commerciale    | 500     | Recherche de prospects, suivi des leads, négociations initiales. |
| WEB3   | Expertise blockchain         | 1000    | Conception et déploiement de contrats intelligents, R&D blockchain. |
| AI     | Expertise IA                 | 1000    | Prompt engineering, conception d’agents IA, architecture IA (LLM, RAG, MCP). |
| INNO   | Conseil en innovation        | 700     | Gestion de projet R&D, études état de l’art, articles et dossiers d’innovation. |

Récap à la fin : liste des codes créés ou mis à jour avec label et TJM en €.
