import apiClient from './client'
import type { EndingArchiveEntry, GameResult, GameSummary, SaveSlot } from '../types'

export function createGame(): Promise<GameSummary> {
  return apiClient.post<GameSummary>('/api/games').then((res) => res.data)
}

export function getSummary(gameId: number): Promise<GameSummary> {
  return apiClient.get<GameSummary>(`/api/games/${gameId}/summary`).then((res) => res.data)
}

export function getResult(gameId: number): Promise<GameResult> {
  return apiClient.get<GameResult>(`/api/games/${gameId}/result`).then((res) => res.data)
}

export function listSaveSlots(userId: number): Promise<SaveSlot[]> {
  return apiClient.get<SaveSlot[]>(`/api/users/${userId}/saves`).then((res) => res.data)
}

export function deleteSaveSlot(userId: number, slotNo: number): Promise<void> {
  return apiClient.delete(`/api/users/${userId}/saves/${slotNo}`).then(() => undefined)
}

export function saveGame(gameId: number, slotNo: number): Promise<GameSummary> {
  return apiClient.post<GameSummary>(`/api/games/${gameId}/save`, { slotNo }).then((res) => res.data)
}

// 실제 복원 대상은 슬롯이 가리키는 게임이다(응답의 gameId가 요청에 쓴 gameId와 다를 수 있음).
export function loadGame(gameId: number, slotNo: number): Promise<GameSummary> {
  return apiClient.post<GameSummary>(`/api/games/${gameId}/load`, { slotNo }).then((res) => res.data)
}

export function deleteGame(gameId: number): Promise<void> {
  return apiClient.delete(`/api/games/${gameId}`).then(() => undefined)
}

export function listEndings(userId: number): Promise<EndingArchiveEntry[]> {
  return apiClient
    .get<EndingArchiveEntry[]>(`/api/users/${userId}/endings`)
    .then((res) => res.data)
}

export function sendHeartbeat(gameId: number): Promise<GameSummary> {
  return apiClient.post<GameSummary>(`/api/games/${gameId}/heartbeat`).then((res) => res.data)
}
