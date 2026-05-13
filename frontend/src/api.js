import axios from 'axios'

export const OWNER_TOKEN_STORAGE_KEY = 'wayfinder.ownerToken'
export const OWNER_TOKEN_HEADER = 'X-Wayfinder-Owner-Token'
export const OWNER_TOKEN_COOKIE = 'WAYFINDER_OWNER_TOKEN'

const baseURL =
  (typeof import.meta !== 'undefined' &&
    import.meta.env &&
    import.meta.env.VITE_API_BASE) ||
  '/api'

const instance = axios.create({
  baseURL,
  timeout: 30000,
  withCredentials: true
})

export function getOwnerToken() {
  return sessionStorage.getItem(OWNER_TOKEN_STORAGE_KEY) || ''
}

export function hasOwnerToken() {
  return Boolean(getOwnerToken())
}

export function setOwnerToken(token) {
  const value = String(token || '').trim()
  if (!value) {
    clearOwnerToken()
    return
  }
  sessionStorage.setItem(OWNER_TOKEN_STORAGE_KEY, value)
  document.cookie = `${OWNER_TOKEN_COOKIE}=${encodeURIComponent(value)}; Path=/api; SameSite=Strict`
  window.dispatchEvent(new CustomEvent('wayfinder-owner-token-changed', { detail: { enabled: true } }))
}

export function clearOwnerToken() {
  sessionStorage.removeItem(OWNER_TOKEN_STORAGE_KEY)
  document.cookie = `${OWNER_TOKEN_COOKIE}=; Path=/api; Max-Age=0; SameSite=Strict`
  window.dispatchEvent(new CustomEvent('wayfinder-owner-token-changed', { detail: { enabled: false } }))
}

instance.interceptors.request.use((config) => {
  const token = getOwnerToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers[OWNER_TOKEN_HEADER] = token
  }
  return config
})

export default instance
