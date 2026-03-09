# Issues

## Bugs

- **get_current_datetime échouait (Unsupported unit: Months)** — Corrigé. `AnalyticsService.endOfMonth()` utilisait `Instant.plus(Period.ofMonths(1))`, ce que `Instant` ne supporte pas.

## Limitations

(Aucune limitation documentée.)

## Différé

- **Streaming des réponses agent** — Remplacer « Processing... » par affichage progressif de la réponse en temps réel. Contenu prévu : endpoint `POST /chat/message/stream` (SSE) ou WebSockets ; `LlmChatService.chatStream()` ; `OpenAiCompatibleLlmClient` avec `stream: true` et parsing des deltas SSE ; frontend `sendChatMessageStream` avec `onChunk`, `onDone`, `signal`. **Note Render** : le SSE peut être fermé après ~60 secondes ; WebSockets sont mieux documentés. Prévoir fallback non-streaming ou migration WebSockets si besoin.

- **Reasoning interne (o1, o3, o4-mini)** — Afficher le raisonnement interne de l'agent. Le raisonnement utilise l'API **Responses** (`/v1/responses`), pas Chat Completions. Paramètres : `reasoning.summary: "auto"` ou `"detailed"` ; événements `response.reasoning_summary_text.delta`. Options : garder Chat Completions (sans reasoning), migrer vers Responses API, ou branche conditionnelle selon le modèle.

- **STT : passer à Whisper** — La Web Speech API (navigateur) fonctionne mais avec des limites (latence, début manquant, précision). Whisper (OpenAI ou self-hosted) offrirait une meilleure reconnaissance. Nécessiterait un endpoint backend pour traiter l’audio.
