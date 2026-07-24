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
  // 저장 확인("예") 클릭 시 호출하고, 완료될 때까지 기다린 뒤에만 다음 단계로 진행한다.
  requestSave: () => Promise<void>
}>()

const emit = defineEmits<{
  done: []
  freezeLight: [boolean]
  burstLight: []
}>()

const SEEN_KEY = 'checkpoint.dayTransitionSeen'
const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const firstTime = typeof window !== 'undefined' && !window.localStorage.getItem(SEEN_KEY)

const stage = ref<'freeze' | 'close' | 'open' | 'static'>(reducedMotion ? 'static' : 'freeze')
const canSkip = ref(false)
// 저장 확인 게이트: 타이머로는 절대 풀리지 않고, 오직 "예"/"아니오" 클릭으로만 false가 된다.
const awaitingSaveConfirm = ref(false)
const savingCheckpoint = ref(false)
const timers: number[] = []

const trustDelta = props.trustAfter - props.trustBefore
const journalText = journalLine(props.infectedAdmittedSoFar, props.isFinalDay)
const nextDayIntro = props.nextDay !== null ? DAY_INTRO_TEXT[props.nextDay] : undefined

function finish() {
  emit('done')
}

function skip() {
  if (!canSkip.value || awaitingSaveConfirm.value) {
    return
  }
  finish()
}

// "서류철 닫힘" 요약이 끝난 시점의 분기점. 마지막 날은 저장 확인 없이 곧장 종료하고,
// 그 외에는 저장 확인 게이트를 연 뒤 사용자 클릭을 기다린다(자동 진행 없음).
function afterCloseSummary() {
  if (props.nextDay === null) {
    emit('freezeLight', false)
    finish()
    return
  }
  awaitingSaveConfirm.value = true
}

async function confirmSaveYes() {
  if (savingCheckpoint.value) {
    return
  }
  savingCheckpoint.value = true
  try {
    await props.requestSave()
  } finally {
    savingCheckpoint.value = false
    proceedAfterConfirm()
  }
}

function confirmSaveNo() {
  proceedAfterConfirm()
}

function proceedAfterConfirm() {
  awaitingSaveConfirm.value = false
  if (reducedMotion) {
    finish()
    return
  }
  stage.value = 'open'
  emit('freezeLight', false)
  emit('burstLight')
  timers.push(window.setTimeout(finish, 900))
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(SEEN_KEY, '1')
  }

  if (reducedMotion) {
    timers.push(window.setTimeout(afterCloseSummary, 1200))
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
  timers.push(window.setTimeout(afterCloseSummary, 400 + 1200))
})

onUnmounted(() => {
  timers.forEach((t) => window.clearTimeout(t))
  emit('freezeLight', false)
})
</script>

<template>
  <div class="day-transition" role="status" aria-live="polite">
    <button v-if="canSkip && !awaitingSaveConfirm" class="skip label-mono" @click="skip">스킵 ▶</button>

    <div v-if="stage === 'static'" class="static-card">
      <p class="label-stencil day-title">{{ dayEnded }}일차 종료</p>
      <div class="stats">
        <p class="label-mono">처리 인원 {{ processedToday }}명</p>
        <p class="label-mono">감염자 허가 {{ infectedAdmittedToday }}건</p>
        <p class="label-mono" :class="{ down: trustDelta < 0 }">
          신뢰도 {{ trustDelta > 0 ? '+' : '' }}{{ trustDelta }}
        </p>
      </div>
      <p class="journal">{{ journalText }}</p>
    </div>

    <template v-else>
      <div class="fold-backdrop" :class="stage"></div>

      <div v-if="stage === 'close'" class="summary">
        <p class="label-stencil day-title" style="animation-delay: 0ms">{{ dayEnded }}일차 종료</p>
        <div class="stats">
          <p class="label-mono stamp-line" style="animation-delay: 150ms">
            처리 인원 {{ processedToday }}명
          </p>
          <p class="label-mono stamp-line" style="animation-delay: 400ms">
            감염자 허가 {{ infectedAdmittedToday }}건
          </p>
          <p
            class="label-mono stamp-line"
            :class="{ down: trustDelta < 0 }"
            style="animation-delay: 650ms"
          >
            신뢰도 {{ trustDelta > 0 ? '+' : '' }}{{ trustDelta }}
          </p>
        </div>
        <p class="journal stamp-line" style="animation-delay: 900ms">{{ journalText }}</p>
      </div>

      <div v-else-if="stage === 'open'" class="next-day">
        <p class="label-stencil next-day-title">{{ nextDay }}일차</p>
        <p v-if="nextDayIntro" class="day-subtitle">{{ nextDayIntro }}</p>
      </div>
    </template>

    <!-- 저장 확인: 애니메이션이 아니라 사용자 응답을 기다리는 블로킹 단계.
         "예"/"아니오" 클릭 전까지는 어떤 타이머로도 다음 단계로 넘어가지 않는다. -->
    <div v-if="awaitingSaveConfirm" class="confirm-gate">
      <p class="confirm-prompt label-stencil">저장하시겠습니까?</p>
      <div class="confirm-actions">
        <button
          class="confirm-btn label-stencil"
          :disabled="savingCheckpoint"
          @click="confirmSaveYes"
        >
          {{ savingCheckpoint ? '저장 중...' : '예' }}
        </button>
        <button
          class="confirm-btn label-stencil"
          :disabled="savingCheckpoint"
          @click="confirmSaveNo"
        >
          아니오
        </button>
      </div>
    </div>
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

.stats p.down {
  color: var(--stamp-red);
}

.journal {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 0.9rem;
  opacity: 0.85;
  font-style: italic;
}

.day-subtitle {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 0.9rem;
  opacity: 0.8;
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

.confirm-gate {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
  padding: 1.5rem 1.75rem;
  background: rgba(14, 18, 16, 0.55);
  border: 1px solid var(--flicker);
  border-radius: 3px;
}

.confirm-prompt {
  margin: 0;
  color: var(--flicker);
  font-size: 1.05rem;
}

.confirm-actions {
  display: flex;
  gap: 0.75rem;
}

.confirm-btn {
  background: transparent;
  color: var(--flicker);
  border: 1px solid var(--flicker);
  border-radius: 2px;
  padding: 0.5rem 1.3rem;
  font-size: 0.85rem;
}

.confirm-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
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
</style>
