<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { DAY_INTRO_TEXT, journalLine } from '../content/story'

const props = defineProps<{
  dayEnded: number
  // null이면 "마지막 날 종료 → 결과 화면"용 변형: 다음 날 열림 단계 없이 요약만 보여주고 끝난다.
  nextDay: number | null
  processedToday: number
  infectedAdmittedToday: number
  trustBefore: number
  trustAfter: number
  infectedAdmittedSoFar: number
  isFinalDay: boolean
}>()

const emit = defineEmits<{
  done: []
  freezeLight: [boolean]
  burstLight: []
  // 3단계(다음 날 도입 문구) 진입 시점. 부모가 이 시점에 다음 방문자를 미리 인계받아
  // 흐릿하게(투명도 15~20%) 보여줄 수 있도록 알려준다 — 마지막 날에는 발생하지 않는다.
  enterIntro: []
}>()

const SEEN_KEY = 'checkpoint.dayTransitionSeen'
const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const firstTime = typeof window !== 'undefined' && !window.localStorage.getItem(SEEN_KEY)

// 도장이 하나씩 등장하는 간격 — 예전에는 250ms라 너무 빨리 연달아 나온다는 피드백을
// 받아 0.8~1초로 늦췄다. 마지막 도장이 다 찍힌 뒤에도 최소 1초는 정지 상태를 유지한
// 다음에야 일지 텍스트가 자동으로 나타난다("하루의 무게를 곱씹게" 하려는 의도된 페이싱).
const STAT_STAMP_INTERVAL_MS = 900
const STAT_STAMP_DURATION_MS = 260
const SUMMARY_HOLD_MS = 1000
const STAT1_DELAY_MS = STAT_STAMP_INTERVAL_MS
const STAT2_DELAY_MS = STAT_STAMP_INTERVAL_MS * 2
const STAT3_DELAY_MS = STAT_STAMP_INTERVAL_MS * 3
const JOURNAL_AUTO_REVEAL_DELAY_MS = STAT3_DELAY_MS + STAT_STAMP_DURATION_MS + SUMMARY_HOLD_MS

const stage = ref<'freeze' | 'close' | 'open' | 'static'>(reducedMotion ? 'static' : 'freeze')
// reduced-motion 전용: 1~2단계를 대신하는 static 카드가 요약+일지(summary)만 보여주는
// 중인지, 3단계(도입 문구 클릭 대기)로 넘어갔는지를 구분한다.
const staticPhase = ref<'summary' | 'intro'>('summary')
const canSkip = ref(false)
// 도장 3개가 다 찍힌 뒤(+ SUMMARY_HOLD_MS 정지) 클릭 없이 자동으로 일지 텍스트가
// 화면에 나타났는지(페이드인 트리거). 도장 요약 영역 자체에는 더 이상 클릭 핸들러가 없다.
const journalRevealed = ref(false)
// 일지 텍스트 자체의 페이드인이 끝나 클릭 가능해졌는지. 오직 이 클릭(onJournalClick)으로만
// 3단계로 넘어간다.
const journalClickable = ref(false)
const timers: number[] = []

const trustDelta = props.trustAfter - props.trustBefore
// 신뢰도 변화가 0인 날("오탐 없이 감염자만 허가")을 "신뢰도가 0이 됐다"로 오독하지
// 않도록, 변화 없음은 반드시 "변화 없음"이라는 문구 + (±0)을 함께 표기한다.
const trustDeltaText =
  trustDelta === 0 ? '신뢰도 변화 없음 (±0)' : `신뢰도 ${trustDelta > 0 ? '+' : ''}${trustDelta}`
const trustDeltaClass = trustDelta < 0 ? 'down' : trustDelta > 0 ? 'up' : 'neutral'
const journalText = journalLine(props.infectedAdmittedSoFar, props.isFinalDay)
const nextDayIntro = props.nextDay !== null ? DAY_INTRO_TEXT[props.nextDay] : undefined

function finish() {
  emit('done')
}

function clearTimers() {
  timers.forEach((t) => window.clearTimeout(t))
  timers.length = 0
}

// 도장 3개가 다 찍힌 시점(또는 static 모드의 요약 표시 시점)에 호출된다. 클릭을
// 기다리지 않고 곧장 일지 텍스트를 화면에 등장시킨다. 도장 자체는 흐리거나 어둡게
// 만들지 않고(선명도 유지), 대신 형광등이 한 번 더 깜빡여 "다음 문장이 나타났음"을
// 알린다 — FlickeringLight가 reduced-motion이면 알아서 무시하므로 여기선 그냥 emit한다.
// reduced-motion에서는 일지 텍스트에도 재생할 페이드인 애니메이션이 없으므로 곧장
// 클릭 가능해지고, 그 외에는 일지 텍스트 자체의 페이드인이 끝난 뒤에야 클릭 가능해진다.
function revealJournal() {
  journalRevealed.value = true
  emit('burstLight')
  if (reducedMotion) {
    journalClickable.value = true
    return
  }
  timers.push(window.setTimeout(() => (journalClickable.value = true), STAT_STAMP_DURATION_MS))
}

// 스킵은 1단계(정지)/2단계 도장 등장 애니메이션의 "대기 시간"만 건너뛴다. 일지 텍스트
// 클릭, 3단계(도입 문구) 클릭은 애니메이션이 아니라 필수 사용자 입력이라 스킵 대상이
// 아니며, 스킵을 누르면 일지 텍스트가 곧장 나타날 뿐 그 이후 클릭은 생략시키지 않는다.
function skip() {
  if (!canSkip.value) {
    return
  }
  clearTimers()
  stage.value = 'close'
  revealJournal()
}

// 일지 텍스트 클릭 시점의 분기점. 마지막 날은 "다음 날 도입 문구" 자체가 없으므로
// 곧장 종료(엔딩 전환)하고, 그 외에는 별도의 "확인" 버튼 없이 3단계(도입 문구)로
// 전환된 뒤 그 문구 클릭을 무기한 기다린다.
function onJournalClick() {
  if (!journalClickable.value) {
    return
  }
  journalClickable.value = false
  if (props.nextDay === null) {
    emit('freezeLight', false)
    finish()
    return
  }
  if (reducedMotion) {
    staticPhase.value = 'intro'
    emit('enterIntro')
    return
  }
  stage.value = 'open'
  emit('freezeLight', false)
  emit('burstLight')
  emit('enterIntro')
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SEEN_KEY, '1')
  }

  if (reducedMotion) {
    timers.push(window.setTimeout(revealJournal, 1200))
    return
  }

  if (!firstTime) {
    timers.push(window.setTimeout(() => (canSkip.value = true), 300))
  }

  emit('freezeLight', true)
  timers.push(
    window.setTimeout(() => {
      stage.value = 'close'
    }, 400),
  )
  timers.push(window.setTimeout(revealJournal, 400 + JOURNAL_AUTO_REVEAL_DELAY_MS))
})

onUnmounted(() => {
  clearTimers()
  emit('freezeLight', false)
})
</script>

<template>
  <div class="day-transition" role="status" aria-live="polite">
    <button
      v-if="canSkip && (stage === 'freeze' || (stage === 'close' && !journalRevealed))"
      class="skip label-mono"
      @click="skip"
    >
      스킵 ▶
    </button>

    <div v-if="stage === 'static'" class="static-card">
      <template v-if="staticPhase === 'summary'">
        <div class="summary-stamp-block">
          <p class="label-stencil day-title">{{ dayEnded }}일차 종료</p>
          <div class="stats">
            <p class="label-mono">처리 인원 {{ processedToday }}명</p>
            <p class="label-mono">감염자 허가 {{ infectedAdmittedToday }}건</p>
            <p class="label-mono" :class="trustDeltaClass">{{ trustDeltaText }}</p>
          </div>
        </div>
        <button
          v-if="journalRevealed"
          type="button"
          class="journal"
          :class="{ 'narrative-trigger': journalClickable }"
          :disabled="!journalClickable"
          @click="onJournalClick"
        >
          {{ journalText }}
        </button>
      </template>
      <template v-else>
        <p class="label-stencil next-day-title">{{ nextDay }}일차</p>
        <button type="button" class="day-intro-trigger narrative-trigger" @click="finish">
          {{ nextDayIntro ?? '다음 날이 밝았다.' }}
        </button>
      </template>
    </div>

    <template v-else>
      <div class="fold-backdrop" :class="stage"></div>

      <div v-if="stage === 'close'" class="summary">
        <div class="summary-stamp-block">
          <p class="label-stencil day-title" style="animation-delay: 0ms">{{ dayEnded }}일차 종료</p>
          <div class="stats">
            <p class="label-mono stamp-line" :style="{ animationDelay: `${STAT1_DELAY_MS}ms` }">
              처리 인원 {{ processedToday }}명
            </p>
            <p class="label-mono stamp-line" :style="{ animationDelay: `${STAT2_DELAY_MS}ms` }">
              감염자 허가 {{ infectedAdmittedToday }}건
            </p>
            <p
              class="label-mono stamp-line"
              :class="trustDeltaClass"
              :style="{ animationDelay: `${STAT3_DELAY_MS}ms` }"
            >
              {{ trustDeltaText }}
            </p>
          </div>
        </div>
        <button
          v-if="journalRevealed"
          type="button"
          class="journal"
          :class="journalClickable ? 'narrative-trigger' : 'stamp-line'"
          :disabled="!journalClickable"
          @click="onJournalClick"
        >
          {{ journalText }}
        </button>
      </div>

      <div v-else-if="stage === 'open'" class="next-day">
        <p class="label-stencil next-day-title">{{ nextDay }}일차</p>
        <button type="button" class="day-intro-trigger narrative-trigger" @click="finish">
          {{ nextDayIntro ?? '다음 날이 밝았다.' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.day-transition {
  position: fixed;
  inset: 0;
  z-index: 110;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.skip {
  position: absolute;
  top: 1rem;
  right: 1rem;
  z-index: 111;
  background: transparent;
  color: var(--flicker);
  border: 1px solid var(--flicker);
  border-radius: 2px;
  padding: 0.3rem 0.7rem;
  font-size: 0.7rem;
  opacity: 0.75;
}

.fold-backdrop {
  position: absolute;
  inset: 0;
  background: var(--void);
  clip-path: inset(0 0 100% 0);
}

.fold-backdrop.close {
  animation: wipe-close 1200ms ease-in-out forwards;
}

.fold-backdrop.open {
  clip-path: inset(0 0 0% 0);
  animation: wipe-open 900ms ease-in-out forwards;
}

.summary,
.next-day,
.static-card {
  position: relative;
  z-index: 1;
  text-align: center;
  color: var(--flicker);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  max-width: 420px;
  padding: 0 1.25rem;
}

.day-title,
.next-day-title {
  font-size: 1.6rem;
  letter-spacing: 0.08em;
}

.stats {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.stats p {
  margin: 0;
  font-size: 1rem;
}

/* 신뢰도 변화 색상 — 하락/상승/변화없음을 색으로도 구분하되, 텍스트 자체("-10"/"+5"/
   "변화 없음 (±0)")가 이미 명시적이라 색약 사용자도 색에 의존하지 않고 판별 가능하다. */
.stats p.down {
  color: var(--stamp-red);
}

.stats p.up {
  color: var(--quarantine);
}

.stats p.neutral {
  color: var(--flicker);
}

/* 도장 3개는 클릭 트리거가 아니다 — 등장 애니메이션이 끝나면 완전히 선명한 상태
   (불투명도 100%, blur 없음)를 그대로 유지하는 순수 표시 영역이며, 최소 SUMMARY_HOLD_MS
   정지 후 클릭 없이 자동으로 일지 텍스트(.journal)가 나타난다. */
.summary-stamp-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  text-align: center;
}

/* 일지 텍스트/도입 문구 둘 다 <button>이지만, 확정적으로 클릭 가능해지기 전까지는
   평범한 서사 텍스트처럼 보여야 한다 — 브라우저 기본 버튼 크롬을 지우고 타이포그래피만
   남긴다. 실제 클릭 가능 여부에 따른 시각 신호(밑줄/커서/깜빡임)는 .narrative-trigger가
   맡는다. */
.journal {
  all: unset;
  display: block;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 0.9rem;
  opacity: 0.85;
  font-style: italic;
  text-align: center;
}

.journal:disabled {
  cursor: default;
}

.day-intro-trigger {
  all: unset;
  display: block;
  margin: 0;
  font-family: var(--font-serif);
  font-size: 0.9rem;
  text-align: center;
}

/* "확인"/"다음" 같은 기능 라벨 버튼을 두지 않고, 서사 텍스트 자체를 클릭 트리거로 쓴다.
   클릭 가능해진 시점에만 밑줄+포인터 커서+은은한 깜빡임으로 그 사실을 담백하게 암시한다. */
.narrative-trigger {
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-color: rgba(232, 222, 196, 0.5);
  animation: intro-pulse 2400ms ease-in-out infinite;
}

.narrative-trigger:hover {
  text-decoration-color: var(--flicker);
}

.narrative-trigger:focus-visible {
  outline: 2px solid var(--flicker);
  outline-offset: 4px;
}

@keyframes intro-pulse {
  0%,
  100% {
    opacity: 0.7;
  }
  50% {
    opacity: 1;
  }
}

.stamp-line {
  opacity: 0;
  filter: blur(6px);
  transform: scale(1.6);
  animation: stat-stamp-in 260ms ease-out forwards;
}

.static-card {
  animation: static-fade 1200ms ease-in-out;
}

@keyframes wipe-close {
  from {
    clip-path: inset(0 0 100% 0);
  }
  to {
    clip-path: inset(0 0 0% 0);
  }
}

@keyframes wipe-open {
  from {
    clip-path: inset(0 0 0% 0);
  }
  to {
    clip-path: inset(0 0 100% 0);
  }
}

@keyframes stat-stamp-in {
  0% {
    opacity: 0;
    filter: blur(6px);
    transform: scale(1.6);
  }
  60% {
    opacity: 1;
    filter: blur(0);
    transform: scale(0.95);
  }
  100% {
    opacity: 1;
    filter: blur(0);
    transform: scale(1);
  }
}

@keyframes static-fade {
  0%,
  100% {
    opacity: 0;
  }
  15%,
  85% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .narrative-trigger {
    animation: none;
  }
}
</style>
