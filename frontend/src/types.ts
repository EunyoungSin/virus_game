export type GameStatus = 'IN_PROGRESS' | 'PAUSED' | 'FINISHED'
export type EndingType = 'BEST' | 'NORMAL' | 'BAD'
export type EndingReason = 'INFECTION_SPREAD' | 'TRUST_COLLAPSE' | 'IDLE_TIMEOUT' | null
export type Decision = 'ADMIT' | 'REJECT'
export type TopicTag = 'TRAVEL' | 'JOB' | 'CONTACT' | 'SYMPTOM' | 'OTHER'

export interface AuthResponse {
  userId: number
  token: string
}

export interface GameSummary {
  gameId: number
  status: GameStatus
  currentDay: number
  processedToday: number
  totalProcessed: number
  trustScore: number
  testKitsRemaining: number
  infectedAdmittedSoFar: number
  createdAt: string
  updatedAt: string
}

export interface GameResult {
  gameId: number
  endingType: EndingType
  endingReason: EndingReason
  infectedAdmitted: number
  innocentRejected: number
  totalProcessed: number
  finalTrustScore: number
  createdAt: string
}

export interface TravelStop {
  city: string
  date: string
}

export interface Visitor {
  visitorId: number
  dayIndex: number
  orderInDay: number
  name: string
  age: number
  jobClaimed: string | null
  originCity: string | null
  travelHistory: TravelStop[] | null
}

export interface AskResponse {
  answer: string
  turnNo: number
}

export interface ConversationTurn {
  turnNo: number
  question: string
  answer: string
  topicTag: TopicTag | null
}

export interface DecisionResult {
  visitorId: number
  decision: Decision
  correct: boolean
  game: GameSummary
  endingType: EndingType | null
}

export interface TestKitResult {
  visitorId: number
  infected: boolean
  testKitsRemaining: number
}

export interface EndingArchiveEntry {
  gameId: number
  endingType: EndingType
  endingReason: EndingReason
  totalProcessed: number
  infectedAdmitted: number
  innocentRejected: number
  finalTrustScore: number
  finishedAt: string
}

// 게임 완전 삭제/목록으로 나가기/슬롯 불러오기·덮어쓰기·삭제 등 모든 확인 모달은 레이아웃이
// 동일하고 텍스트/색상만 다르므로, 개별 컴포넌트 대신 이 config로 <ConfirmModal />을 채워 쓴다.
export interface ConfirmModalConfig {
  eyebrow: string
  title: string
  tag?: string
  body: string
  destination?: string
  warning?: string
  watermark: string
  watermarkVariant: 'stampRed' | 'quarantine'
  confirmLabel: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

// 저장 슬롯은 게임이 아니라 유저 전역 자원(최대 5개)이다. "사건 이어하기" 화면은 이 목록 그 자체다.
export interface SaveSlot {
  slotNo: number
  occupied: boolean
  gameId: number | null
  day: number | null
  trustScore: number | null
  savedAt: string | null
  gameStatus: GameStatus | null
  endingType: EndingType | null
  endingReason: EndingReason
}
