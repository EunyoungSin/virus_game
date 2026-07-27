<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { DAY_INTRO_TEXT, INTRO_BRIEFING } from '../content/story'

const emit = defineEmits<{ dismiss: [] }>()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const phase = ref<'letter' | 'day-title'>('letter')
const revealed = ref(reducedMotion)
const timers: number[] = []

function goToDayTitle() {
  timers.forEach((t) => window.clearTimeout(t))
  phase.value = 'day-title'
  timers.push(window.setTimeout(dismiss, reducedMotion ? 900 : 1400))
}

function dismiss() {
  emit('dismiss')
}

function onPrimaryAction() {
  if (phase.value === 'letter') {
    goToDayTitle()
  } else {
    dismiss()
  }
}

let previousBodyOverflow = ''
let previousHtmlOverflow = ''

onMounted(() => {
  if (!reducedMotion) {
    timers.push(
      window.setTimeout(() => {
        revealed.value = true
      }, 2400),
    )
  }

  // 전체화면 오버레이 뒤로 GamePlayView 본문이 그대로 남아있어 문서 자체가
  // 스크롤될 수 있다 — 오버레이가 떠 있는 동안은 .scroll-area 하나만 스크롤
  // 되도록 잠근다. tokens.css가 html에 overflow-x를 명시해두면 뷰포트 스크롤을
  // body가 아니라 html(documentElement)이 담당하게 되므로, 둘 다 잠가야 한다.
  previousBodyOverflow = document.body.style.overflow
  previousHtmlOverflow = document.documentElement.style.overflow
  document.body.style.overflow = 'hidden'
  document.documentElement.style.overflow = 'hidden'
})

onUnmounted(() => {
  timers.forEach((t) => window.clearTimeout(t))
  document.body.style.overflow = previousBodyOverflow
  document.documentElement.style.overflow = previousHtmlOverflow
})
</script>

<template>
  <div class="intro-briefing" role="dialog" aria-label="근무 배치 공문">
    <button class="skip label-mono" @click="dismiss">스킵 ▶</button>

    <div class="scroll-area" :class="{ 'has-fixed-action': phase === 'letter' }">
      <div v-if="phase === 'letter'" class="letter-wrap">
        <pre class="letter">{{ INTRO_BRIEFING }}</pre>
        <div v-if="!reducedMotion" class="reveal-mask" :class="{ revealed }" aria-hidden="true"></div>
      </div>

      <div v-else class="day-title-wrap">
        <p class="day-title label-stencil">1일차</p>
        <p class="day-subtitle">{{ DAY_INTRO_TEXT[1] }}</p>
      </div>
    </div>

    <div v-if="phase === 'letter'" class="action-bar">
      <button class="primary label-stencil" @click="onPrimaryAction">
        {{ revealed ? '심사 시작' : '스킵' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.intro-briefing {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: var(--void);
  display: flex;
  flex-direction: column;
}

.skip {
  position: fixed;
  top: calc(1rem + env(safe-area-inset-top));
  right: 1rem;
  z-index: 4;
  background: transparent;
  color: var(--flicker);
  border: 1px solid var(--flicker);
  border-radius: 2px;
  padding: 0.3rem 0.7rem;
  font-size: 0.7rem;
  opacity: 0.75;
}

.scroll-area {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  display: flex;
  justify-content: center;
  padding: calc(2rem + 2.25rem) 1.25rem 2rem;
}

.scroll-area.has-fixed-action {
  padding-bottom: calc(4.5rem + env(safe-area-inset-bottom));
}

/* 화면 크기와 무관하게 항상 탭 가능하도록, 텍스트 스크롤 영역과 분리된
   화면 하단 고정 액션 바에 둔다 (iOS 안전 영역 확보). */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3;
  display: flex;
  justify-content: center;
  padding: 0.75rem 1.25rem calc(0.75rem + env(safe-area-inset-bottom));
  background: linear-gradient(to top, var(--void) 60%, transparent);
}

.letter-wrap {
  position: relative;
  max-width: 480px;
  width: 100%;
  align-self: flex-start;
}

.letter {
  position: relative;
  z-index: 1;
  background: var(--paper);
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 0.95rem;
  line-height: 1.7;
  white-space: pre-wrap;
  padding: 1.75rem 1.5rem;
  border-radius: 2px;
  box-shadow: var(--shadow-doc);
  margin: 0;
  width: 100%;
}

/* 종이 한 장이 스캔되듯 위→아래로 드러나야 한다 — 가려진 영역(void 마스크)의
   top-inset을 늘려 아래로 밀어내는 방식으로, 위쪽부터 먼저 드러나게 한다. */
.reveal-mask {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: var(--void);
  clip-path: inset(0 0 0 0);
  animation: reveal 2400ms linear forwards;
}

.reveal-mask.revealed {
  clip-path: inset(100% 0 0 0);
}

.primary {
  position: relative;
  z-index: 1;
  padding: 0.75rem 1.5rem;
  background: var(--ink);
  color: var(--paper);
  border: none;
  border-radius: 3px;
}

.day-title-wrap {
  text-align: center;
  color: var(--flicker);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.day-title {
  font-size: 2rem;
  letter-spacing: 0.08em;
  margin: 0;
}

.day-subtitle {
  margin: 0;
  opacity: 0.8;
  font-family: var(--font-serif);
}

@keyframes reveal {
  from {
    clip-path: inset(0 0 0 0);
  }
  to {
    clip-path: inset(100% 0 0 0);
  }
}
</style>
