const AUTH_CHANGE_EVENT = 'cineo-auth-change'

function notifyAuthChange() {
  window.dispatchEvent(new Event(AUTH_CHANGE_EVENT))
}

export function getAccessToken() {
  return localStorage.getItem('accessToken')
}

export function isLoggedIn() {
  return getAccessToken() !== null
}

export function getRefreshToken() {
  return localStorage.getItem('refreshToken')
}

export function saveTokens(accessToken, refreshToken) {
  localStorage.setItem('accessToken', accessToken)
  if (refreshToken) {
    localStorage.setItem('refreshToken', refreshToken)
  }
  notifyAuthChange()
}

export function clearTokens() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  notifyAuthChange()
}

// 토큰이 저장·삭제되면(다른 탭 포함) callback을 부른다. useSyncExternalStore에 넘겨 쓴다
export function subscribeAuth(callback) {
  window.addEventListener(AUTH_CHANGE_EVENT, callback)
  window.addEventListener('storage', callback)
  return () => {
    window.removeEventListener(AUTH_CHANGE_EVENT, callback)
    window.removeEventListener('storage', callback)
  }
}
