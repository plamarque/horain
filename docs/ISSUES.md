# Issues

## Bugs

- **get_current_datetime échouait (Unsupported unit: Months)** — Corrigé. `AnalyticsService.endOfMonth()` utilisait `Instant.plus(Period.ofMonths(1))`, ce que `Instant` ne supporte pas.

## Limitations

- **STT sur mobile** — Sur mobile (Chrome Android notamment), la Web Speech API interrompt l'enregistrement après ~0,5 s de silence. Le transcript capturé jusque-là est inséré dans l'input ; l'utilisateur peut recliquer sur le micro pour continuer et ajouter du texte à la suite. Parlant par étapes, les segments s'ajoutent bout à bout.

## Différé

- **Streaming des réponses agent** — Remplacer « Processing... » par affichage progressif de la réponse en temps réel. **État actuel** : `StreamingLlmClient` existe avec méthode `chatStream()` ; `OpenAiCompatibleLlmClient` implémente l'interface avec un stub qui délègue à `chat()` et envoie tout le contenu d'un coup (code compilable, non fonctionnel pour le streaming). **À faire** : endpoint `POST /chat/message/stream` (SSE) ou WebSockets ; `LlmChatService.chatStream()` ; `OpenAiCompatibleLlmClient` avec `stream: true` et parsing des deltas SSE ; frontend `sendChatMessageStream` avec `onChunk`, `onDone`, `signal`. **Note Cloud Run** : le SSE peut être fermé après ~60 secondes ; WebSockets sont mieux documentés. Prévoir fallback non-streaming ou migration WebSockets si besoin.

- **Reasoning interne (o1, o3, o4-mini)** — Afficher le raisonnement interne de l'agent. Le raisonnement utilise l'API **Responses** (`/v1/responses`), pas Chat Completions. Paramètres : `reasoning.summary: "auto"` ou `"detailed"` ; événements `response.reasoning_summary_text.delta`. Options : garder Chat Completions (sans reasoning), migrer vers Responses API, ou branche conditionnelle selon le modèle.

- **STT : passer à Whisper** — La Web Speech API (navigateur) fonctionne mais avec des limites (latence, début manquant, précision). Whisper (OpenAI ou self-hosted) offrirait une meilleure reconnaissance. Nécessiterait un endpoint backend pour traiter l'audio.

- **Waveform réaliste** — Afficher une waveform basée sur le niveau audio réel pour indiquer visuellement si le son est reçu. Remplacer l'animation décorative actuelle (fallback quand getUserMedia échoue ou avant captation) par un état « pas de signal » ou une indication claire (barres plates). Objectif : feedback visuel fiable pour l'utilisateur.
