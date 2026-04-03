#!/usr/bin/env node
/**
 * Compatibility wrapper. Canonical script moved to langsmith/scripts.
 */

import path from 'path'
import { fileURLToPath } from 'url'

const here = path.dirname(fileURLToPath(import.meta.url))
const target = path.join(here, '..', '..', 'langsmith', 'scripts', 'run-evaluation.mjs')
await import(`file://${target}`)
