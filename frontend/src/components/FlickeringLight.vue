<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{
  paused?: boolean
  // 값이 바뀔 때마다(카운터 증가) 평소보다 불안정한 이중 깜빡임을 한 번 재생한다.
  burstToken?: number
}>()

const dimmed = ref(false)
const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const timers: number[] = []

function clearTimers() {
  while (timers.length) {
    window.clearTimeout(timers.pop())
  }
}

function scheduleFlicker() {
  if (reducedMotion || props.paused) {
    return
  }
  const delayMs = 3000 + Math.random() * 5000
  timers.push(
    window.setTimeout(() => {
      dimmed.value = true
      const dimMs = 90 + Math.random() * 140
      timers.push(
        window.setTimeout(() => {
          dimmed.value = false
          scheduleFlicker()
        }, dimMs),
      )
    }, delayMs),
  )
}

watch(
  () => props.paused,
  (paused) => {
    if (paused) {
      clearTimers()
      dimmed.value = false
    } else {
      scheduleFlicker()
    }
  },
)

watch(
  () => props.burstToken,
  () => {
    if (reducedMotion) {
      return
    }
    clearTimers()
    dimmed.value = true
    timers.push(
      window.setTimeout(() => {
        dimmed.value = false
        timers.push(
          window.setTimeout(() => {
            dimmed.value = true
            timers.push(
              window.setTimeout(() => {
                dimmed.value = false
                if (!props.paused) {
                  scheduleFlicker()
                }
              }, 90),
            )
          }, 130),
        )
      }, 110),
    )
  },
)

onMounted(() => {
  if (!props.paused) {
    scheduleFlicker()
  }
})

onUnmounted(clearTimers)
</script>

<template>
  <div class="fluorescent-light" :class="{ dimmed }" aria-hidden="true"></div>
</template>

<style scoped>
.fluorescent-light {
  height: 3px;
  width: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    var(--flicker) 15%,
    var(--flicker) 85%,
    transparent
  );
  opacity: 0.5;
  box-shadow: 0 0 14px 2px var(--flicker);
  transition: opacity 0.06s linear;
}

.fluorescent-light.dimmed {
  opacity: 0.12;
  box-shadow: 0 0 4px 1px var(--flicker);
}
</style>
