/// <reference types="vite/client" />

declare const __APP_VERSION__: string
declare const __GIT_SHA__: string

interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_API_KEY: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
