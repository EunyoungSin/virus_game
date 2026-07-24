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

onMounted(() => {
  if (!reducedMotion) {
    timers.push(
      window.setTimeout(() => {
        revealed.value = true
      }, 2400),
    )
  }
})

onUnmounted(() => {
  timers.forEach((t) => window.clearTimeout(t))
})
</script>

<template>
  <div class="intro-briefing" role="dialog" aria-label="근무 배치 공문">
    <button class="skip label-mono" @click="dismiss">스킵 ▶</button>

    <div v-if="phase === 'letter'" class="letter-wrap">
      <pre class="letter">{{ INTRO_BRIEFING }}</pre>
      <div v-if="!reducedMotion" class="reveal-mask" :class="{ revealed }" aria-hidden="true"></div>
      <button class="primary label-stencil" @click="onPrimaryAction">
        {{ revealed ? '심사 시작' : '스킵' }}
      </button>
    </div>

    <div v-else class="day-title-wrap">
      <p class="day-title label-stencil">1일차</p>
      <p class="day-subtitle">{{ DAY_INTRO_TEXT[1] }}</p>
    </div>
  </div>
</template>

<style scoped>
.intro-briefing {
  position: fixed;
  inset: 0;
  z-index: 120;
  overflow: hidden;
  background: var(--void);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1.25rem;
}

.skip {
  position: absolute;
  top: 1rem;
  right: 1rem;
  background: transparent;
  color: var(--flicker);
  border: 1px solid var(--flicker);
  border-radius: 2px;
  padding: 0.3rem 0.7rem;
  font-size: 0.7rem;
  opacity: 0.75;
}

.letter-wrap {
  position: relative;
  max-width: 480px;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
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

.reveal-mask {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: var(--void);
  clip-path: inset(0 0 0% 0);
  animation: reveal 2400ms linear forwards;
}

.reveal-mask.revealed {
  clip-path: inset(0 0 100% 0);
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
    clip-path: inset(0 0 0% 0);
  }
  to {
    clip-path: inset(0 0 100% 0);
  }
}
</style>
