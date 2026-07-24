const DEVICE_ID_KEY = 'checkpoint.deviceId'
const TOKEN_KEY = 'checkpoint.token'
const USER_ID_KEY = 'checkpoint.userId'

export function getOrCreateDeviceId(): string {
  let deviceId = localStorage.getItem(DEVICE_ID_KEY)
  if (!deviceId) {
    deviceId = crypto.randomUUID()
    localStorage.setItem(DEVICE_ID_KEY, deviceId)
  }
  return deviceId
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function loadAuth(): { token: string | null; userId: number | null } {
  const token = localStorage.getItem(TOKEN_KEY)
  const rawUserId = localStorage.getItem(USER_ID_KEY)
  return { token, userId: rawUserId ? Number(rawUserId) : null }
}

export function saveAuth(token: string, userId: number): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_ID_KEY, String(userId))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_ID_KEY)
}
