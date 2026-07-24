<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getResult } from '../api/games'
import { endingVerdictLabel, resolveEndingEpilogue } from '../content/story'
import FlickeringLight from '../components/FlickeringLight.vue'
import type { EndingType, GameResult } from '../types'

const route = useRoute()
const router = useRouter()
const gameId = Number(route.params.gameId)

const result = ref<GameResult | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const epilogue = computed(() =>
  result.value
    ? resolveEndingEpilogue(result.value.endingType, result.value.endingReason, result.value.totalProcessed)
    : null,
)

const ENDING_TONE: Record<EndingType, string> = {
  BEST: 'tone-best',
  NORMAL: 'tone-normal',
  BAD: 'tone-bad',
}

const verdictLabel = computed(() =>
  result.value ? endingVerdictLabel(result.value.endingType, result.value.endingReason) : '',
)

onMounted(async () => {
  try {
    result.value = await getResult(gameId)
  } catch {
    errorMessage.value = '결과를 불러오지 못했습니다. 사건이 아직 종결되지 않았을 수 있습니다.'
  } finally {
    loading.value = false
  }
})

function goToTitle() {
  router.push({ name: 'title' })
}

function goToArchive() {
  router.push({ name: 'archive' })
}
</script>

<template>
  <div class="closing-page">
    <FlickeringLight />
    <section class="closing">
      <p class="eyebrow label-mono">CASE CLOSED</p>
      <h1 class="label-stencil">사건 종결 보고서</h1>

      <p v-if="loading" class="loading-line label-mono">보고서 작성 중...</p>
      <p v-else-if="errorMessage" class="error">{{ errorMessage }}</p>

      <div v-else-if="result" class="report" :class="ENDING_TONE[result.endingType]">
        <p class="verdict label-stencil">{{ verdictLabel }}</p>
        <dl class="stats">
          <div class="stat">
            <dt class="label-stencil">최종 신뢰도</dt>
            <dd class="label-mono">{{ result.finalTrustScore }}</dd>
          </div>
          <div class="stat">
            <dt class="label-stencil">총 처리 인원</dt>
            <dd class="label-mono">{{ result.totalProcessed }}명</dd>
          </div>
          <div class="stat">
            <dt class="label-stencil">감염자 통과 건수</dt>
            <dd class="label-mono">{{ result.infectedAdmitted }}건</dd>
          </div>
          <div class="stat">
            <dt class="label-stencil">무고한 방문자 거부 건수</dt>
            <dd class="label-mono">{{ result.innocentRejected }}건</dd>
          </div>
        </dl>

        <div v-if="epilogue" class="epilogue">
          <p class="epilogue-heading label-stencil">{{ epilogue.heading }}</p>
          <p class="epilogue-body">{{ epilogue.body }}</p>
        </div>
      </div>

      <div class="actions">
        <button class="label-stencil" @click="goToTitle">타이틀로</button>
        <button class="secondary label-stencil" @click="goToArchive">기록 보관소에서 보기</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.closing-page {
  min-height: 100vh;
}

.closing {
  max-width: 480px;
  margin: 3rem auto;
  padding: 0 1.25rem 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.eyebrow {
  color: var(--flicker);
  opacity: 0.75;
  font-size: 0.75rem;
  margin: 0;
}

h1 {
  margin: 0.25rem 0 0;
  letter-spacing: 0.06em;
}

.loading-line {
  color: var(--paper);
  opacity: 0.75;
}

.error {
  color: var(--stamp-red);
}

.report {
  background: var(--paper);
  color: var(--ink);
  border-radius: 3px;
  padding: 1.5rem;
  box-shadow: var(--shadow-doc);
  animation: settle-in 260ms ease-out;
  border-left: 6px solid var(--ink);
}

.report.tone-best {
  border-left-color: var(--quarantine);
}

.report.tone-bad {
  border-left-color: var(--stamp-red);
}

.verdict {
  font-size: 1.3rem;
  margin: 0 0 1.1rem;
}

.tone-bad .verdict {
  color: var(--stamp-red);
}

.tone-best .verdict {
  color: var(--quarantine);
}

.stats {
  display: grid;
  gap: 0.6rem;
  margin: 0;
}

.stat {
  display: flex;
  justify-content: space-between;
  border-bottom: 1px dashed var(--paper-dark);
  padding-bottom: 0.35rem;
  gap: 1rem;
}

.stat dt {
  font-size: 0.85rem;
  opacity: 0.8;
}

.stat dd {
  margin: 0;
}

.epilogue {
  margin-top: 1.1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--paper-dark);
}

.epilogue-heading {
  margin: 0 0 0.6rem;
  font-size: 0.85rem;
  opacity: 0.75;
}

.epilogue-body {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 0.9rem;
  line-height: 1.7;
  white-space: pre-line;
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

button {
  padding: 0.75rem 1.5rem;
  background: var(--ink);
  color: var(--paper);
  border: none;
  border-radius: 3px;
}

button.secondary {
  background: transparent;
  color: var(--flicker);
  border: 1px solid var(--flicker);
}

@keyframes settle-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
