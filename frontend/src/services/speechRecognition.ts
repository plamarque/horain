/**
 * Speech recognition service using the browser Web Speech API.
 * Provides startListening() and stopListening() with transcription callback.
 * Falls back to null (no-op) when API is not supported.
 */

export type OnTranscript = (text: string) => void
export type OnStatus = (status: 'listening' | 'ready' | 'stopped' | 'error') => void
export type OnErrorDetail = (error: string) => void
export type OnInterim = (text: string) => void

let recognition: SpeechRecognition | null = null

/**
 * Check if the Web Speech API (SpeechRecognition) is supported.
 * Chrome: SpeechRecognition. Safari: webkitSpeechRecognition.
 */
export function isSpeechRecognitionSupported(): boolean {
  if (typeof window === 'undefined') return false
  const w = window as Window & {
    SpeechRecognition?: unknown
    webkitSpeechRecognition?: unknown
  }
  return !!(w.SpeechRecognition || w.webkitSpeechRecognition)
}

/**
 * Start listening for speech. Call stopListening() when done.
 * @param onTranscript - Callback with the final transcription when recognition ends.
 * @param onStatus - Optional callback for status changes (listening, stopped, error).
 * @param onErrorDetail - Optional callback with error code when error occurs.
 * @param onInterim - Optional callback with interim transcript (live feedback while speaking).
 * @returns true if started, false if API not supported.
 */
export function startListening(
  onTranscript: OnTranscript,
  onStatus?: OnStatus,
  onErrorDetail?: OnErrorDetail,
  onInterim?: OnInterim
): boolean {
  if (!isSpeechRecognitionSupported()) {
    onStatus?.('error')
    return false
  }

  const SpeechRecognitionAPI =
    (window as Window & { SpeechRecognition?: new () => SpeechRecognition })
      .SpeechRecognition ||
    (window as Window & { webkitSpeechRecognition?: new () => SpeechRecognition })
      .webkitSpeechRecognition

  if (!SpeechRecognitionAPI) {
    onStatus?.('error')
    return false
  }

  recognition = new SpeechRecognitionAPI()
  recognition.continuous = true
  recognition.interimResults = true
  // Use en-US for English locale, fr-FR for French (matches demo phrase "30 minutes on HatCast")
  const lang = navigator.language?.toLowerCase()
  recognition.lang = lang?.startsWith('en') ? (lang || 'en-US') : lang?.startsWith('fr') ? (lang || 'fr-FR') : 'en-US'

  let finalTranscript = ''
  let pendingInterim = ''

  recognition.onresult = (event: SpeechRecognitionEvent) => {
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i]
      const text = result[0].transcript
      if (result.isFinal) {
        finalTranscript += (finalTranscript || pendingInterim ? ' ' : '') + text
        pendingInterim = ''
      } else {
        pendingInterim = text
        onInterim?.(finalTranscript + (finalTranscript ? ' ' : '') + text)
      }
    }
  }

  recognition.onend = () => {
    onStatus?.('stopped')
    const fullTranscript = (finalTranscript + (finalTranscript && pendingInterim ? ' ' : '') + pendingInterim).trim()
    if (fullTranscript) {
      onTranscript(fullTranscript)
    }
  }

  recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
    onErrorDetail?.(event.error || 'unknown')
    onStatus?.('error')
  }

  // audiostart = engine is actually capturing; user can speak now (reduces missed beginning)
  recognition.addEventListener('audiostart', () => {
    onStatus?.('ready')
  })
  // Fallback: if audiostart never fires (e.g. some mobile browsers), assume ready after 800ms
  setTimeout(() => onStatus?.('ready'), 800)

  recognition.start()
  onStatus?.('listening')
  return true
}

/**
 * Stop listening and finalize transcription.
 * The onTranscript callback from startListening will be invoked with the result.
 */
export function stopListening(): void {
  if (recognition) {
    recognition.stop()
    recognition = null
  }
}
