import apiClient from './client'
import type { Decision, DecisionResult } from '../types'

export function decide(gameId: number, visitorId: number, decision: Decision): Promise<DecisionResult> {
  return apiClient
    .post<DecisionResult>(`/api/games/${gameId}/visitors/${visitorId}/decision`, { decision })
    .then((res) => res.data)
}
