<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { Decision } from '../types'

const props = defineProps<{ decision: Decision }>()
const emit = defineEmits<{ done: [] }>()

const shaking = ref(false)
const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const label = computed(() => (props.decision === 'ADMIT' ? '통과' : '거부'))
const toneClass = computed(() => (props.decision === 'ADMIT' ? 'tone-admit' : 'tone-reject'))

const timers: number[] = []

onMounted(() => {
  if (reducedMotion) {
    timers.push(window.setTimeout(() => emit('done'), 320))
    return
  }
  timers.push(
    window.setTimeout(() => {
      shaking.value = true
    }, 340),
  )
  timers.push(
    window.setTimeout(() => {
      shaking.value = false
    }, 500),
  )
  timers.push(window.setTimeout(() => emit('done'), 620))
})

onUnmounted(() => {
  timers.forEach((t) => window.clearTimeout(t))
})
</script>

<template>
  <div class="stamp-viewport" aria-hidden="false">
    <div
      class="stamp-overlay"
      :class="{ 'reduced-motion': reducedMotion, shaking }"
      role="status"
      :aria-label="decision === 'ADMIT' ? '통과 판정 확정' : '거부 판정 확정'"
    >
      <div class="stamp-backdrop"></div>
      <div class="stamp-mark" :class="toneClass">
        <span class="stamp-ring">
          <span class="stamp-label label-stencil">{{ label }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stamp-viewport {
  position: fixed;
  inset: 0;
  z-index: 100;
  overflow: hidden;
  pointer-events: none;
}

.stamp-overlay {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stamp-overlay.shaking {
  animation: stamp-screen-shake 160ms ease-out;
}

.stamp-backdrop {
  position: absolute;
  inset: 0;
  background: var(--void);
  opacity: 0;
  animation: stamp-backdrop-in 180ms ease-out forwards;
}

.reduced-motion .stamp-backdrop {
  animation: none;
  opacity: 0.55;
}

.stamp-mark {
  position: relative;
  filter: blur(6px);
  opacity: 0;
  transform: scale(3) rotate(-10deg);
  animation: stamp-impact 480ms cubic-bezier(0.2, 0.9, 0.3, 1) forwards;
}

.reduced-motion .stamp-mark {
  animation: none;
  opacity: 1;
  filter: none;
  transform: scale(1) rotate(-4deg);
}

.stamp-ring {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 11rem;
  min-height: 11rem;
  padding: 1.5rem;
  border-radius: 50%;
  border: 6px double currentColor;
  position: relative;
}

.stamp-ring::after {
  content: '';
  position: absolute;
  inset: -14px;
  border-radius: 50%;
  background: radial-gradient(circle, currentColor 0%, transparent 70%);
  opacity: 0.22;
  filter: blur(5px);
}

.tone-admit {
  color: var(--ink);
}

.tone-reject {
  color: var(--stamp-red);
}

.stamp-label {
  font-size: 2.2rem;
  font-weight: 700;
}

@keyframes stamp-backdrop-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 0.55;
  }
}

@keyframes stamp-impact {
  0% {
    opacity: 0;
    filter: blur(10px);
    transform: scale(3) rotate(-12deg);
  }
  55% {
    opacity: 1;
    filter: blur(0);
    transform: scale(0.92) rotate(-3deg);
  }
  75% {
    transform: scale(1.06) rotate(-6deg);
  }
  100% {
    opacity: 1;
    filter: blur(0);
    transform: scale(1) rotate(-4deg);
  }
}

@keyframes stamp-screen-shake {
  0%,
  100% {
    transform: translate(0, 0);
  }
  20% {
    transform: translate(-3px, 2px);
  }
  40% {
    transform: translate(3px, -2px);
  }
  60% {
    transform: translate(-2px, -2px);
  }
  80% {
    transform: translate(2px, 2px);
  }
}
</style>
