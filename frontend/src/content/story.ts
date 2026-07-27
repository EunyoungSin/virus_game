import type { EndingReason, EndingType } from '../types'

// 이 스토리 텍스트는 기본 게임 구조(4일 × 3명 = 12명)를 그대로 서사화한 것이라
// GAME_DAYS/GAME_VISITORS_PER_DAY 환경변수를 바꿔도 자동으로 늘어나지 않는다.
export const TOTAL_VISITORS = 12
export const FINAL_DAY = 4

export const INTRO_BRIEFING = `[내부 공문 발췌 — 열람 등급: 검문소 근무자]

귀하는 오늘부로 제 7 검문소 심사관으로 배치된다.

봉쇄는 유지된다. 도시 밖으로도, 안으로도.
심사관의 판단은 최종 결정으로 간주된다. 상급자의 재검토는 없다.

방문자는 겉으로 판별되지 않는다.
문답으로 판단하라. 문답만으로.

[부기 — 감염 증상 안내]
방역 당국이 확인한 증상은 다음 세 가지다.

  1. 기침
  2. 발열
  3. 정신착란 (문답 중 말이 앞뒤가 맞지 않거나, 답을 흐리는 증세 포함)

단, 위 증상은 감염자에게서만 나타나는 것은 아니다.
평범한 이유로도 기침이나 발열을 보이는 자가 있을 수 있으며,
반대로 증상이 전혀 없는 감염자도 있다.
증상은 참고할 뿐, 증거가 아니다.

...본인의 안전을 위해서라도, 이 자리를 오래 비우지 않기를 권고한다.

— 제 7 검문소 근무 수칙 1항`

export const DAY_INTRO_TEXT: Record<number, string> = {
  1: '오늘도 평소와 같은 하루가 될 것이다.',
  2: '어제 몇 명을 들여보냈는지, 아직도 손이 기억한다.',
  3: '상부에서 처리 속도를 올리라는 지시가 내려왔다.',
  4: '오늘이 지나면, 이 봉쇄가 어떤 식으로든 끝난다.',
}

const JOURNAL_TEXT = [
  '오늘도 통과시킨 감염자는 없다. 그뿐이다.',
  '몇 명이었는지는 기억하는데, 어느 얼굴이었는지는 가물가물하다.',
  '숫자를 세는 것도 이제 의미가 없어진 것 같다.',
]

const DAY4_JOURNAL_TEXT = [
  '내일이면 이 자리를 떠날 수 있을지도 모른다.',
  '내일 아침 도시가 어떤 모습일지, 상상하고 싶지 않다.',
  '창밖이 조용하다. 그게 더 무섭다.',
]

function journalTier(infectedAdmittedSoFar: number): 0 | 1 | 2 {
  if (infectedAdmittedSoFar === 0) return 0
  if (infectedAdmittedSoFar <= 2) return 1
  return 2
}

export function journalLine(infectedAdmittedSoFar: number, isFinalDay: boolean): string {
  const tier = journalTier(infectedAdmittedSoFar)
  return (isFinalDay ? DAY4_JOURNAL_TEXT : JOURNAL_TEXT)[tier]
}

export interface EndingEpilogue {
  heading: string
  body: string
}

const EPILOGUES: Record<'BEST' | 'NORMAL' | 'BAD_A' | 'BAD_B' | 'BAD_C', EndingEpilogue> = {
  BEST: {
    heading: '[속보]',
    body: `백신이 개발되었다. 봉쇄가 해제된다.

거리에 사람들이 나온다. 당신은 그중 누구의 얼굴도
정확히 기억하지 못한다 — 창구 너머로만 마주했으니까.

그래도, 아무도 잃지 않았다.
그거면 됐다고, 당신은 생각하기로 한다.`,
  },
  NORMAL: {
    heading: '[속보]',
    body: `백신이 개발되었다. 봉쇄가 해제된다.

하지만 며칠 전 당신이 들여보낸 몇몇으로 인해,
도시 안에서 조용히 몇 사람이 세상을 떠났다.

이름은 공개되지 않는다. 당신도 알 방법이 없다.

문 앞에서 봤던 표정들이, 가끔 떠오른다.`,
  },
  BAD_A: {
    heading: '[최종 보고서 — 작성자 불명]',
    body: `도시는 더 이상 응답하지 않는다.

이 보고서는 원래 다른 필체로 시작되었다.
후반부로 갈수록, 글씨가 흔들린다.

...몸이 무겁다. 기침이 멈추지 않는다.
그래도 마지막 방문자까지는 판정을 마쳐야 한다고,
당신은 되뇐다.

기록은 여기서 끊긴다.`,
  },
  BAD_B: {
    heading: '[속보 — 검문소 앞 상황 보고]',
    body: `오늘, 거부당한 이들이 창구 앞에 모였다.

당신은 규정대로 판단했을 뿐이라고 되뇌지만,
문 너머의 함성은 그런 걸 궁금해하지 않는다.

상부와의 통신이 끊긴다.
서류는 아직 책상 위에 쌓여 있는데,
더 이상 심사할 사람이 남아있지 않다.

— 이후 기록 없음.`,
  },
  BAD_C: {
    heading: '[비상 기록 — 미완성]',
    body: `창구 유리가 깨졌다.

당신이 자리를 비운 사이, 밖에서 기다리던 이들이
더는 기다리지 못하고 안으로 밀려들어왔다.

몇 명이었는지는 셀 겨를도 없었다.
그중 누가 감염자였는지도, 이제는 중요하지 않다.

본인의 안전을 위해서라도,
이 자리를 오래 비우지 않기를 권고한다.

근무 수칙 1항이 떠오른다.

— 이후 기록 없음.`,
  },
}

// BAD 엔딩의 에필로그는 ending_reason으로 직접 분기한다: 감염 확산(BAD_A),
// 신뢰 붕괴(BAD_B), 유휴 타임아웃(BAD_C). endingReason이 없으면(구 데이터 등)
// 총 처리 인원으로 감염 확산/신뢰 붕괴만 추정한다.
export function resolveEndingEpilogue(
  endingType: EndingType,
  endingReason: EndingReason,
  totalProcessed: number,
): EndingEpilogue {
  if (endingType !== 'BAD') {
    return EPILOGUES[endingType]
  }
  if (endingReason === 'IDLE_TIMEOUT') return EPILOGUES.BAD_C
  if (endingReason === 'TRUST_COLLAPSE') return EPILOGUES.BAD_B
  if (endingReason === 'INFECTION_SPREAD') return EPILOGUES.BAD_A
  return totalProcessed < TOTAL_VISITORS ? EPILOGUES.BAD_B : EPILOGUES.BAD_A
}

// 결과 화면/기록 보관소에서 공용으로 쓰는 엔딩 타이틀. BAD는 ending_reason으로 분기하고,
// reason이 없으면(구 데이터 등) 일반화된 "심사 실패"로 표시한다.
const BAD_REASON_LABELS: Record<string, string> = {
  INFECTION_SPREAD: '감염 확산',
  TRUST_COLLAPSE: '신뢰 붕괴',
  IDLE_TIMEOUT: '근무지 이탈',
}

export function endingVerdictLabel(endingType: EndingType, endingReason: EndingReason): string {
  if (endingType === 'BEST') return '모범 심사관'
  if (endingType === 'NORMAL') return '통상 종결'
  return (endingReason && BAD_REASON_LABELS[endingReason]) || '심사 실패'
}
