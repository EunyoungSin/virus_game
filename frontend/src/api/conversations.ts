import apiClient from './client'
import type { AskResponse, ConversationTurn, TopicTag } from '../types'

export function ask(
  gameId: number,
  visitorId: number,
  question: string,
  topicTag: TopicTag | null,
): Promise<AskResponse> {
  return apiClient
    .post<AskResponse>(`/api/games/${gameId}/visitors/${visitorId}/conversations`, {
      question,
      topicTag,
    })
    .then((res) => res.data)
}

export function getHistory(gameId: number, visitorId: number): Promise<ConversationTurn[]> {
  return apiClient
    .get<ConversationTurn[]>(`/api/games/${gameId}/visitors/${visitorId}/conversations`)
    .then((res) => res.data)
}
