<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listEndings } from '../api/games'
import { endingVerdictLabel } from '../content/story'
import FlickeringLight from '../components/FlickeringLight.vue'
import { useAuthStore } from '../stores/auth'
import type { EndingArchiveEntry } from '../types'

const auth = useAuthStore()
const endings = ref<EndingArchiveEntry[]>([])
const loading = ref(true)
const errorMessage = ref('')

async function loadEndings() {
  loading.value = true
  errorMessage.value = ''
  try {
    endings.value = await listEndings(auth.userId as number)
  } catch {
    errorMessage.value = '엔딩 기록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

function endingLabel(entry: EndingArchiveEntry): string {
  return endingVerdictLabel(entry.endingType, entry.endingReason)
}

function toneClass(entry: EndingArchiveEntry): string {
  if (entry.endingType === 'BEST') return 'tone-best'
  if (entry.endingType === 'BAD') return 'tone-bad'
  return 'tone-normal'
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

onMounted(loadEndings)
</script>

<template>
  <div class="archive-page">
    <FlickeringLight />
    <section class="archive">
      <header class="archive-header">
        <router-link :to="{ name: 'title' }" class="link label-stencil">← 타이틀로</router-link>
        <div>
          <p class="eyebrow label-mono">ENDING ARCHIVE</p>
          <h1 class="label-stencil">기록 보관소</h1>
        </div>
      </header>

      <p v-if="loading" class="loading-line label-mono">기록 열람 중...</p>
      <p v-else-if="errorMessage" class="error">{{ errorMessage }}</p>
      <p v-else-if="endings.length === 0" class="hint">아직 종결된 사건이 없습니다.</p>

      <ul v-else class="entries">
        <li v-for="entry in endings" :key="entry.gameId" class="entry" :class="toneClass(entry)">
          <div class="entry-head">
            <span class="verdict label-stencil">{{ endingLabel(entry) }}</span>
            <span class="finished-at label-mono">{{ formatDate(entry.finishedAt) }}</span>
          </div>
          <dl class="stats">
            <div class="stat">
              <dt class="label-stencil">처리 인원</dt>
              <dd class="label-mono">{{ entry.totalProcessed }}명</dd>
            </div>
            <div class="stat">
              <dt class="label-stencil">감염자 통과</dt>
              <dd class="label-mono">{{ entry.infectedAdmitted }}건</dd>
            </div>
            <div class="stat">
              <dt class="label-stencil">무고한 거부</dt>
              <dd class="label-mono">{{ entry.innocentRejected }}건</dd>
            </div>
            <div class="stat">
              <dt class="label-stencil">최종 신뢰도</dt>
              <dd class="label-mono">{{ entry.finalTrustScore }}</dd>
            </div>
          </dl>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.archive-page {
  min-height: 100vh;
}

.archive {
  max-width: 640px;
  margin: 2rem auto;
  padding: 0 1rem 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.archive-header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.link {
  align-self: flex-start;
  color: var(--flicker);
  text-decoration: underline;
  font-size: 0.85rem;
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

.hint,
.loading-line {
  color: var(--paper);
  opacity: 0.75;
}

.error {
  color: var(--stamp-red);
}

.entries {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.entry {
  background: var(--paper);
  color: var(--ink);
  border-radius: 3px;
  padding: 1.1rem 1.25rem;
  box-shadow: var(--shadow-doc);
  border-left: 6px solid var(--ink);
}

.entry.tone-best {
  border-left-color: var(--quarantine);
}

.entry.tone-bad {
  border-left-color: var(--stamp-red);
}

.entry-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 0.75rem;
}

.verdict {
  font-size: 1.05rem;
}

.tone-bad .verdict {
  color: var(--stamp-red);
}

.tone-best .verdict {
  color: var(--quarantine);
}

.finished-at {
  font-size: 0.7rem;
  opacity: 0.6;
}

.stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.5rem 1rem;
  margin: 0;
}

.stat {
  display: flex;
  justify-content: space-between;
  border-bottom: 1px dashed var(--paper-dark);
  padding-bottom: 0.25rem;
  gap: 0.5rem;
}

.stat dt {
  font-size: 0.75rem;
  opacity: 0.75;
}

.stat dd {
  margin: 0;
}

@media (max-width: 480px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
