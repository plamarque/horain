#!/usr/bin/env node
/**
 * Compatibility wrapper. Canonical script lives under langsmith/scripts.
 */

import path from 'path'
import { fileURLToPath } from 'url'

const here = path.dirname(fileURLToPath(import.meta.url))
const target = path.join(here, '..', 'langsmith', 'scripts', 'diagnose-dataset-visibility.mjs')
await import(`file://${target}`)
