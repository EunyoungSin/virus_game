<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const props = defineProps<{ infected: boolean }>()
const emit = defineEmits<{ done: [] }>()

// 판정 도장(StampOverlay)과 톤을 절대 섞지 않기 위한 별도 컴포넌트. 도장은 "내가 방금
// 내린 결정"을 쾅 찍어 확정하는 임팩트 모션이고, 이건 "이미 정해져 있던 사실"이 조용히
// 옆에서 밀려나와 드러나는 것뿐이라 화면 흔들림·블러 임팩트·경고색 점멸을 전혀 쓰지 않는다.
const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const leaving = ref(false)
const timers: number[] = []

onMounted(() => {
  const holdMs = reducedMotion ? 1400 : 2600
  const leaveMs = reducedMotion ? 0 : 400
  timers.push(
    window.setTimeout(() => {
      leaving.value = true
      timers.push(window.setTimeout(() => emit('done'), leaveMs))
    }, holdMs),
  )
})

onUnmounted(() => {
  timers.forEach((t) => window.clearTimeout(t))
})
</script>

<template>
  <div
    class="test-kit-slip"
    :class="{ leaving, 'reduced-motion': reducedMotion }"
    role="status"
    :aria-label="`검사 결과: ${props.infected ? '양성' : '음성'}`"
  >
    <p class="slip-label label-mono">검사 결과</p>
    <p class="slip-value label-mono" :class="props.infected ? 'positive' : 'negative'">
      <span aria-hidden="true">{{ props.infected ? '⚠' : '✓' }}</span>
      {{ props.infected ? '양성' : '음성' }}
    </p>
  </div>
</template>

<style scoped>
/* 프린터 영수증처럼 카드 옆에서 스르륵 밀려나오는 느낌 — 스케일/블러 임팩트, 화면 흔들림
   없이 위치 이동 + 페이드만 쓴다. 결과 텍스트도 도장처럼 찍히지 않고 그냥 색이 있는
   텍스트로만 표기한다(양성=stamp-red, 음성=quarantine). */
.test-kit-slip {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  z-index: 6;
  background: var(--paper);
  color: var(--ink);
  border: 1px solid var(--paper-dark);
  border-radius: 2px;
  padding: 0.45rem 0.7rem;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.3);
  animation: slip-in 450ms ease-out forwards;
}

.test-kit-slip.leaving {
  animation: slip-out 400ms ease-in forwards;
}

.test-kit-slip.reduced-motion {
  animation: none;
}

.test-kit-slip.reduced-motion.leaving {
  display: none;
}

.slip-label {
  margin: 0 0 0.15rem;
  font-size: 0.65rem;
  opacity: 0.6;
  white-space: nowrap;
}

.slip-value {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 0.03em;
  white-space: nowrap;
}

.slip-value.positive {
  color: var(--stamp-red);
}

.slip-value.negative {
  color: var(--quarantine);
}

@keyframes slip-in {
  from {
    transform: translateX(55%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes slip-out {
  from {
    transform: translateX(0);
    opacity: 1;
  }
  to {
    transform: translateX(55%);
    opacity: 0;
  }
}
</style>
