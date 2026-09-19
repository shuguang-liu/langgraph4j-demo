import { reactive } from 'vue'

// 登录态：token + 永久 threadId，持久化在 localStorage，刷新不丢
const STORAGE_KEY = 'lg4j_auth'

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {}
  } catch {
    return {}
  }
}

export const auth = reactive({
  token: '',
  threadId: '',
  username: '',
  expireAt: 0,   // token 过期时间（毫秒），0 = 永久
  guest: false,  // 游客模式：不发 token，服务端用 IP 当 threadId 并限 5 次
  ...load()
})

export function saveAuth(patch) {
  Object.assign(auth, patch)
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...auth }))
}

export function clearAuth() {
  auth.token = ''
  auth.threadId = ''
  auth.username = ''
  auth.expireAt = 0
  auth.guest = false
  localStorage.removeItem(STORAGE_KEY)
}

export function isLoggedIn() {
  return !!auth.token && (!auth.expireAt || auth.expireAt > Date.now())
}

// token 已过期：页面加载时用于提示重新登录
export function isTokenExpired() {
  return !!auth.token && !!auth.expireAt && auth.expireAt <= Date.now()
}
