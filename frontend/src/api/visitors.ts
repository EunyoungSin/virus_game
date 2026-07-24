import apiClient from './client'
import type { TestKitResult, Visitor } from '../types'

export function nextVisitor(gameId: number): Promise<Visitor> {
  return apiClient.get<Visitor>(`/api/games/${gameId}/next-visitor`).then((res) => res.data)
}

export function useTestKit(gameId: number, visitorId: number): Promise<TestKitResult> {
  return apiClient
    .post<TestKitResult>(`/api/games/${gameId}/visitors/${visitorId}/test-kit`)
    .then((res) => res.data)
}
