import { defineStore } from 'pinia'
import { guestLogin as guestLoginRequest } from '../api/auth'
import { clearAuth, getOrCreateDeviceId, loadAuth, saveAuth } from '../lib/authStorage'

const initial = loadAuth()

export const useAuthStore = defineStore('auth', {
  state: () => ({
    userId: initial.userId as number | null,
    token: initial.token as string | null,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.userId),
  },
  actions: {
    async guestLogin() {
      const deviceId = getOrCreateDeviceId()
      const response = await guestLoginRequest(deviceId)
      this.userId = response.userId
      this.token = response.token
      saveAuth(response.token, response.userId)
    },
    logout() {
      this.userId = null
      this.token = null
      clearAuth()
    },
  },
})
