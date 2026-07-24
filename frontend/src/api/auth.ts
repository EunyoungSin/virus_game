import apiClient from './client'
import type { AuthResponse } from '../types'

export function guestLogin(deviceId: string): Promise<AuthResponse> {
  return apiClient.post<AuthResponse>('/api/auth/guest', { deviceId }).then((res) => res.data)
}
