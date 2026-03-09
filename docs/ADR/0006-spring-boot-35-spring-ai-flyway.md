# ADR-0006 : Spring Boot 3.5, Spring AI 1.1.2, Flyway 11

## Contexte

- Flyway 9 (embarqué dans Spring Boot 3.2) affiche un warning : PostgreSQL 17.6 (Supabase) n'est pas officiellement supporté (« support has not been tested »).
- Override Flyway 10 sur Spring Boot 3.2 provoque une incompatibilité d'API (licenseKey).
- Spring AI offre une intégration native pour les APIs OpenAI-compatibles (OpenAI, OpenRouter, LiteLLM).

## Décision

- **Spring Boot 3.5.11** : version à jour, stable.
- **Spring AI 1.1.2** : ChatModel, tool calling, multi-provider (OpenAI, OpenRouter).
- **Flyway 11** : via flyway-core + flyway-database-postgresql (PostgreSQL 17 supporté).
- **LLM Client** : SpringAiLlmClient utilise ChatModel en priorité ; fallback OpenAiCompatibleLlmClient si ChatModel absent.

## Conséquences

- Le warning Flyway/PostgreSQL 17 disparaît.
- Configuration Spring AI : spring.ai.openai.* (api-key, base-url, chat.options.model), alignée avec llm.* pour compatibilité.
- Le package llm/ conserve l’interface LlmClient ; l’implémentation Spring AI s’ajoute à côté de l’existant.
- Java 21 (migré depuis Java 17) : alignement avec Spring Boot 3.5 / Spring AI 1.1, virtual threads, LTS jusqu'en 2031.
- Tests unitaires backend : passent. Tests e2e : 6/8 passent (2 nécessitent LLM_API_KEY pour les scénarios chat avancés).
