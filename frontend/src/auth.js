export function clearTokens() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
}
