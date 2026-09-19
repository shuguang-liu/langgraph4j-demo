import { auth } from './auth'

// 直连后端时设置 VITE_API_BASE（如 http://127.0.0.1:19091）；默认走 Vite 代理（同源）
const BASE = import.meta.env.VITE_API_BASE || ''

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

async function post(path, body, { token, raw = false } = {}) {
  let res
  try {
    res = await fetch(BASE + path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {})
      },
      body: JSON.stringify(body)
    })
  } catch (e) {
    throw new ApiError(0, '网络错误：无法连接服务端，请确认后端已启动（默认 19091 端口）')
  }

  if (raw) {
    // Agentic RAG 接口直接返回纯文本回答
    if (!res.ok) await failWith(res)
    return res.text()
  }

  const data = await res.json().catch(() => ({}))
  if (!res.ok) await failWith(res, data)
  return data
}

async function failWith(res, data = {}) {
  throw new ApiError(res.status, data.message || data.error || `请求失败（HTTP ${res.status}）`)
}

export const api = {
  login: (username, password) => post('/api/auth/login', { username, password }),
  logout: (token) => post('/api/auth/logout', {}, { token }),
  ragSearch: (body) => post('/api/agenticRag/ragSearch', body, { token: auth.token, raw: true }),
  reActSearch: (body) => post('/api/reAct/reActSearch', body, { token: auth.token }),
  reActApprove: (body) => post('/api/reAct/approve', body, { token: auth.token }),
  multiAsk: (body) => post('/api/multi/ask', body, { token: auth.token }),
  multiApprove: (body) => post('/api/multi/approve', body, { token: auth.token })
}
