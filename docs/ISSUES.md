# Issues

## Bugs

- **get_current_datetime échouait (Unsupported unit: Months)** — Corrigé. `AnalyticsService.endOfMonth()` utilisait `Instant.plus(Period.ofMonths(1))`, ce que `Instant` ne supporte pas.

## Limitations

(Aucune limitation documentée.)

## Différé

- **STT : passer à Whisper** — La Web Speech API (navigateur) fonctionne mais avec des limites (latence, début manquant, précision). Whisper (OpenAI ou self-hosted) offrirait une meilleure reconnaissance. Nécessiterait un endpoint backend pour traiter l’audio.
