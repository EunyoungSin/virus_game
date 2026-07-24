<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ loading?: boolean }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const impact = ref(false)

async function onConfirm() {
  if (props.loading) {
    return
  }
  if (reducedMotion) {
    emit('confirm')
    return
  }
  impact.value = true
  await new Promise((resolve) => window.setTimeout(resolve, 280))
  emit('confirm')
}

function onCancel() {
  if (props.loading) {
    return
  }
  emit('cancel')
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    onCancel()
  }
}
</script>

<template>
  <div class="backdrop" @keydown="onKeydown">
    <div
      class="confirm-card"
      :class="{ shake: impact }"
      role="alertdialog"
      aria-labelledby="confirmTitle"
      aria-describedby="confirmBody"
    >
      <p class="eyebrow label-mono">근무지 이탈 승인 요청</p>
      <h2 id="confirmTitle" class="label-stencil">목록으로 나가기</h2>
      <p id="confirmBody" class="body-text">저장 이후 진행한 판정과 대화는 모두 사라집니다.</p>
      <p class="destination-line label-mono">→ 사건 이어하기 목록으로 이동</p>
      <p class="warning-line label-mono">계속하시겠습니까?</p>

      <div class="actions">
        <button class="btn label-stencil" :disabled="loading || impact" @click="onCancel">취소</button>
        <button
          class="btn destructive label-stencil"
          :class="{ impact }"
          :disabled="loading || impact"
          @click="onConfirm"
        >
          {{ loading ? '이동 중...' : '확인' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(14, 18, 16, 0.82);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fade-in 0.18s ease;
}

.confirm-card {
  position: relative;
  background: var(--paper);
  color: var(--ink);
  width: min(380px, 88%);
  padding: 28px 26px 24px;
  transform: rotate(-0.6deg);
  box-shadow: 0 24px 50px rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(0, 0, 0, 0.15);
  animation: pop-in 0.22s ease;
  overflow: hidden;
}

/* "자리를 비운다"는 행동에 맞춘 워터마크 — 근무 수칙 1항과 같은 결. */
.confirm-card::after {
  content: '이탈 승인';
  position: absolute;
  top: 46%;
  left: 50%;
  transform: translate(-50%, -50%) rotate(-18deg);
  font-family: var(--font-stencil);
  font-size: 40px;
  letter-spacing: 0.15em;
  color: var(--quarantine);
  opacity: 0.13;
  white-space: nowrap;
  pointer-events: none;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--quarantine);
  margin: 0 0 8px;
  position: relative;
}

h2 {
  font-size: 21px;
  letter-spacing: 0.02em;
  margin: 0 0 14px;
  position: relative;
  line-height: 1.4;
}

.body-text {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(33, 29, 24, 0.85);
  margin: 0 0 6px;
  position: relative;
}

.destination-line {
  font-size: 12px;
  color: rgba(33, 29, 24, 0.55);
  margin: 0 0 14px;
  position: relative;
}

.warning-line {
  font-size: 12px;
  color: var(--stamp-red);
  letter-spacing: 0.01em;
  line-height: 1.6;
  margin: 0 0 22px;
  position: relative;
  border-left: 2px solid var(--stamp-red);
  padding-left: 8px;
}

.actions {
  display: flex;
  gap: 10px;
  position: relative;
}

.btn {
  flex: 1;
  font-size: 14px;
  letter-spacing: 0.03em;
  padding: 11px 14px;
  cursor: pointer;
  border: 2px solid var(--ink);
  background: transparent;
  color: var(--ink);
  border-radius: 2px;
  transition: background 0.12s ease;
}

.btn:hover {
  background: rgba(33, 29, 24, 0.06);
}

.btn:focus-visible {
  outline: 3px solid var(--flicker);
  outline-offset: 2px;
}

.btn.destructive {
  border-color: var(--stamp-red);
  color: var(--stamp-red);
  font-weight: 600;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.impact {
  animation: stamp-hit 0.28s ease;
}

.confirm-card.shake {
  animation: shake 0.28s ease;
}

@keyframes stamp-hit {
  0% {
    transform: scale(1);
    filter: blur(0);
  }
  35% {
    transform: scale(1.08);
    filter: blur(1px);
  }
  100% {
    transform: scale(1);
    filter: blur(0);
  }
}

@keyframes shake {
  0%,
  100% {
    transform: rotate(-0.6deg) translate(0, 0);
  }
  25% {
    transform: rotate(-0.6deg) translate(2px, -1px);
  }
  50% {
    transform: rotate(-0.6deg) translate(-2px, 1px);
  }
  75% {
    transform: rotate(-0.6deg) translate(1px, -1px);
  }
}

@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes pop-in {
  from {
    transform: rotate(-0.6deg) scale(0.94);
    opacity: 0;
  }
  to {
    transform: rotate(-0.6deg) scale(1);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .backdrop,
  .confirm-card {
    animation: none;
  }
}
</style>
