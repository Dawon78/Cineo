import { getAccessToken, getRefreshToken, saveTokens, clearTokens } from '../auth'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8082'

// 여러 요청이 동시에 401을 받아도 토큰 재발급은 한 번만 하도록 진행 중인 재발급을 공유한다
let refreshing = null

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return false
  }
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) {
      return false
    }
    const data = await response.json()
    saveTokens(data.accessToken)
    return true
  } catch {
    return false
  }
}

function send(path, { body, headers, ...options }) {
  const token = getAccessToken()
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(token && { Authorization: `Bearer ${token}` }),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function apiFetch(path, options = {}) {
  const response = await send(path, options)
  if (response.status !== 401 || !getAccessToken() || path.startsWith('/api/auth/')) {
    return response
  }

  refreshing ??= refreshAccessToken().finally(() => {
    refreshing = null
  })
  if (!(await refreshing)) {
    clearTokens()
    return response
  }
  return send(path, options)
}
