const TokenKey = 'Admin-Token'
const UserKey = 'Admin-User'

export function getToken() {
  return sessionStorage.getItem(TokenKey)
}

export function setToken(token) {
  return sessionStorage.setItem(TokenKey, token)
}

export function removeToken() {
  return sessionStorage.removeItem(TokenKey)
}

export function getUsername() {
  return sessionStorage.getItem(UserKey)
}

export function setUsername(name) {
  return sessionStorage.setItem(UserKey, name)
}

export function removeUsername() {
  return sessionStorage.removeItem(UserKey)
}
