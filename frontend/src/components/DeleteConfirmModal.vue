<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ caseNumber: number }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const impact = ref(false)

async function onConfirm() {
  if (reducedMotion) {
    emit('confirm')
    return
  }
  impact.value = true
  await new Promise((resolve) => window.setTimeout(resolve, 280))
  emit('confirm')
}

function onCancel() {
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
      <p class="eyebrow label-mono">폐기 승인 요청</p>
      <h2 id="confirmTitle" class="label-stencil">사건 #{{ props.caseNumber }} 삭제</h2>
      <p id="confirmBody" class="body-text">사건 #{{ props.caseNumber }}을 완전히 삭제합니다.</p>
      <p class="warning-line label-mono">되돌릴 수 없습니다.</p>

      <div class="actions">
        <button class="btn label-stencil" :disabled="impact" @click="onCancel">취소</button>
        <button
          class="btn destructive label-stencil"
          :class="{ impact }"
          :disabled="impact"
          @click="onConfirm"
        >
          삭제
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

.confirm-card::after {
  content: '폐기 대상';
  position: absolute;
  top: 46%;
  left: 50%;
  transform: translate(-50%, -50%) rotate(-18deg);
  font-family: var(--font-stencil);
  font-size: 42px;
  letter-spacing: 0.15em;
  color: var(--stamp-red);
  opacity: 0.12;
  white-space: nowrap;
  pointer-events: none;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--stamp-red);
  margin: 0 0 8px;
  position: relative;
}

h2 {
  font-size: 22px;
  letter-spacing: 0.03em;
  margin: 0 0 14px;
  position: relative;
}

.body-text {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(33, 29, 24, 0.85);
  margin: 0 0 6px;
  position: relative;
}

.warning-line {
  font-size: 12px;
  color: var(--stamp-red);
  letter-spacing: 0.02em;
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
