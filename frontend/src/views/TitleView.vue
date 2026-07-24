<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createGame } from '../api/games'
import FlickeringLight from '../components/FlickeringLight.vue'

const router = useRouter()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const caseRef = ref('')
const shaking = ref(false)
const impactAction = ref<'new' | 'continue' | 'archive' | null>(null)
const creating = ref(false)
const errorMessage = ref('')

onMounted(() => {
  caseRef.value = String(Math.floor(100000 + Math.random() * 899999))
})

async function playImpact(action: 'new' | 'continue' | 'archive') {
  if (reducedMotion) {
    return
  }
  impactAction.value = action
  shaking.value = true
  await new Promise((resolve) => window.setTimeout(resolve, 280))
  impactAction.value = null
  shaking.value = false
}

async function startNewGame() {
  creating.value = true
  errorMessage.value = ''
  const impact = playImpact('new')
  try {
    const game = await createGame()
    await impact
    router.push({ name: 'game-play', params: { gameId: game.gameId } })
  } catch {
    await impact
    errorMessage.value = '게임을 시작하지 못했습니다.'
  } finally {
    creating.value = false
  }
}

async function goToContinue() {
  await playImpact('continue')
  router.push({ name: 'game-list' })
}

async function goToArchive() {
  await playImpact('archive')
  router.push({ name: 'archive' })
}
</script>

<template>
  <div class="stage">
    <FlickeringLight />
    <div class="vignette" aria-hidden="true"></div>

    <div class="case-card" :class="{ shake: shaking }">
      <p class="eyebrow label-mono">제 7 검문소 — 근무 단말</p>
      <h1 class="label-stencil">검문소</h1>
      <p class="subtitle">도시는 봉쇄되어 있다.<br />오늘도 문답으로 판단해야 한다.</p>
      <p class="case-no label-mono">CASE REF. #{{ caseRef }}</p>

      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

      <div class="actions">
        <button
          class="stamp-btn primary label-stencil"
          :class="{ impact: impactAction === 'new' }"
          :disabled="creating"
          @click="startNewGame"
        >
          {{ creating ? '서류 준비 중...' : '신규 사건 개시' }}
        </button>
        <button
          class="stamp-btn label-stencil"
          :class="{ impact: impactAction === 'continue' }"
          @click="goToContinue"
        >
          사건 이어하기
        </button>
        <button
          class="stamp-btn danger-outline label-stencil"
          :class="{ impact: impactAction === 'archive' }"
          @click="goToArchive"
        >
          기록 보관소
        </button>
      </div>
    </div>

    <p class="footer-note label-mono">근무 수칙 1항 — 자리를 오래 비우지 말 것</p>
  </div>
</template>

<style scoped>
.stage {
  position: relative;
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
}

.vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(ellipse at center, transparent 45%, rgba(0, 0, 0, 0.55) 100%);
}

.case-card {
  position: relative;
  z-index: 1;
  background: var(--paper);
  color: var(--ink);
  width: min(420px, 92%);
  padding: 40px 32px 32px;
  transform: rotate(-1.2deg);
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.5), 0 2px 0 rgba(0, 0, 0, 0.2) inset;
  border: 1px solid rgba(0, 0, 0, 0.15);
}

.case-card::before {
  content: '';
  position: absolute;
  top: -10px;
  left: 24px;
  width: 34px;
  height: 20px;
  background: rgba(0, 0, 0, 0.25);
  transform: rotate(3deg);
}

.case-card.shake {
  animation: shake 0.28s ease;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--quarantine);
  margin: 0 0 6px;
}

h1 {
  font-size: 34px;
  letter-spacing: 0.06em;
  margin: 0 0 8px;
}

.subtitle {
  font-size: 14px;
  line-height: 1.6;
  color: rgba(33, 29, 24, 0.75);
  margin: 0 0 28px;
}

.case-no {
  font-size: 12px;
  color: rgba(33, 29, 24, 0.5);
  margin: 0 0 22px;
}

.error {
  color: var(--stamp-red);
  font-size: 0.85rem;
  margin: 0 0 1rem;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stamp-btn {
  position: relative;
  font-size: 15px;
  letter-spacing: 0.05em;
  background: transparent;
  border: 2px solid var(--ink);
  color: var(--ink);
  padding: 12px 16px;
  text-align: left;
}

.stamp-btn:hover:not(:disabled) {
  background: rgba(33, 29, 24, 0.06);
}

.stamp-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.stamp-btn.primary {
  border-color: var(--quarantine);
  color: var(--quarantine);
  font-weight: 600;
}

.stamp-btn.danger-outline {
  border-color: var(--stamp-red);
  color: var(--stamp-red);
}

.stamp-btn.impact {
  animation: stampHit 0.28s ease;
}

.footer-note {
  position: relative;
  z-index: 1;
  margin-top: 22px;
  font-size: 10px;
  color: rgba(200, 194, 171, 0.35);
  text-align: center;
}

@keyframes stampHit {
  0% {
    transform: scale(1);
    filter: blur(0);
  }
  35% {
    transform: scale(1.06);
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
    transform: rotate(-1.2deg) translate(0, 0);
  }
  25% {
    transform: rotate(-1.2deg) translate(2px, -1px);
  }
  50% {
    transform: rotate(-1.2deg) translate(-2px, 1px);
  }
  75% {
    transform: rotate(-1.2deg) translate(1px, -1px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stamp-btn.impact,
  .case-card.shake {
    animation: none;
  }
}
</style>
