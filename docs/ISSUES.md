# Issues

## Bugs

- **get_current_datetime échouait (Unsupported unit: Months)** — Corrigé. `AnalyticsService.endOfMonth()` utilisait `Instant.plus(Period.ofMonths(1))`, ce que `Instant` ne supporte pas.

- **Tableau entrées obsolètes après « basculer tout en facturable »** — Corrigé. L’agent appelait `update_time_log` pour chaque entrée puis `propose_entries` avec l’ancienne liste ; le backend affichait les arguments de `propose_entries` tels quels. Désormais `LlmChatService` fusionne les résultats des appels `update_time_log`/`create_time_log` dans les entrées affichées (par id), donc le tableau reflète l’état réel après mise à jour.

- **Confirmation « set 4 to billable and 4 to non-billable » pour « bascule tout en facturable »** — Corrigé. L’agent décrivait l’état actuel au lieu de l’action : l’utilisateur demandait de tout passer en facturable, la confirmation devait être « set all N entries to billable ». Règle MASS UPDATE GUARD précisée dans le prompt ; eval ajouté dans `promptfoo/tests/billable.yaml`.

## Limitations

- **STT sur mobile** — Sur mobile (Chrome Android notamment), la Web Speech API interrompt l'enregistrement après ~0,5 s de silence. Le transcript capturé jusque-là est inséré dans l'input ; l'utilisateur peut recliquer sur le micro pour continuer et ajouter du texte à la suite. Parlant par étapes, les segments s'ajoutent bout à bout.

## Différé

- **Reasoning interne (o1, o3, o4-mini)** — Afficher le raisonnement interne de l'agent. Le raisonnement utilise l'API **Responses** (`/v1/responses`), pas Chat Completions. Paramètres : `reasoning.summary: "auto"` ou `"detailed"` ; événements `response.reasoning_summary_text.delta`. Options : garder Chat Completions (sans reasoning), migrer vers Responses API, ou branche conditionnelle selon le modèle.

- **STT : passer à Whisper** — La Web Speech API (navigateur) fonctionne mais avec des limites (latence, début manquant, précision). Whisper (OpenAI ou self-hosted) offrirait une meilleure reconnaissance. Nécessiterait un endpoint backend pour traiter l'audio.

- **Waveform réaliste** — Afficher une waveform basée sur le niveau audio réel pour indiquer visuellement si le son est reçu. Remplacer l'animation décorative actuelle (fallback quand getUserMedia échoue ou avant captation) par un état « pas de signal » ou une indication claire (barres plates). Objectif : feedback visuel fiable pour l'utilisateur.
