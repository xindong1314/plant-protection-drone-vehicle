const ABSOLUTE_URL_RE = /^(?:https?:)?\/\//i
const INLINE_URL_RE = /^(?:data|blob):/i

/**
 * Resolve a backend-owned asset path through the same base path used by REST calls.
 * Stored inspection images currently use paths such as `/uploads/...`; without the
 * API prefix they would be requested from the frontend origin and bypass Vite or
 * the production gateway proxy.
 */
export function resolveApiAssetUrl(value) {
  if (value == null) return ''

  const raw = String(value).trim()
  if (!raw || ABSOLUTE_URL_RE.test(raw) || INLINE_URL_RE.test(raw)) return raw

  const base = String(import.meta.env.VITE_APP_BASE_API || '').trim().replace(/\/+$/, '')
  const path = raw.replace(/^\/+/, '')
  if (!base || base === '/') return `/${path}`

  if (raw === base || raw.startsWith(`${base}/`)) return raw
  return `${base}/${path}`
}
