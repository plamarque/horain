import { processQueue } from '../sync/syncEngine'

/**
 * Listen to network online event and trigger sync.
 */
export function initNetworkListener(): void {
  if (typeof window === 'undefined') return
  window.addEventListener('online', () => {
    processQueue()
  })
}

initNetworkListener()
