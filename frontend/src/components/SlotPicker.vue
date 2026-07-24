<script setup lang="ts">
import { computed, ref } from 'vue'
import FlickeringLight from './FlickeringLight.vue'
import type { SaveSlot } from '../types'

const props = defineProps<{
  slots: SaveSlot[]
  mode: 'SAVE' | 'LOAD'
  scope: 'GLOBAL' | 'CURRENT_GAME'
  loadingSlotNo?: number | null
}>()

// 타이틀로 바로 나가는 링크는 게임 화면에서 "목록으로"를 눌러 뜬 CURRENT_GAME 선택 화면에서만
// 보여준다. 타이틀에서 곧장 들어온 사건 이어하기(GLOBAL) 화면은 "취소하고 돌아가기" 자체가 이미
// 타이틀로 돌아가는 동작이라 중복이다.
const showTitleLink = computed(() => props.mode === 'LOAD' && props.scope === 'CURRENT_GAME')

const emit = defineEmits<{
  select: [slotNo: number]
  deleteSlot: [slotNo: number]
  cancel: []
}>()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const impactSlotNo = ref<number | null>(null)
const confirmingDeleteSlotNo = ref<number | null>(null)
let deleteConfirmTimer: number | null = null

const copy = computed(() => {
  if (props.mode === 'SAVE') {
    return {
      eyebrow: '기록 보존 — 슬롯 선택',
      title: '저장할 슬롯을 선택하세요',
      note: '이미 기록이 있는 슬롯을 고르면 덮어씁니다.',
    }
  }
  if (props.scope === 'GLOBAL') {
    return {
      eyebrow: '사건 이어하기',
      title: '이어서 진행할 사건을 선택하세요',
      note: '선택한 시점 이후의 판정과 대화는 사라집니다.',
    }
  }
  return {
    eyebrow: '기록 열람 — 슬롯 선택',
    title: '되돌릴 시점을 선택하세요',
    note: '선택한 슬롯 이후의 판정과 대화는 모두 사라집니다.',
  }
})

function formatSavedAt(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function isInert(slot: SaveSlot): boolean {
  return !slot.occupied && props.mode === 'LOAD'
}

async function onSlotClick(slot: SaveSlot) {
  if (isInert(slot) || props.loadingSlotNo != null) {
    return
  }
  if (!reducedMotion) {
    impactSlotNo.value = slot.slotNo
    await new Promise((resolve) => window.setTimeout(resolve, 280))
    impactSlotNo.value = null
  }
  emit('select', slot.slotNo)
}

function onDeleteClick(slotNo: number, event: MouseEvent) {
  event.stopPropagation()
  if (confirmingDeleteSlotNo.value !== slotNo) {
    confirmingDeleteSlotNo.value = slotNo
    if (deleteConfirmTimer !== null) {
      window.clearTimeout(deleteConfirmTimer)
    }
    deleteConfirmTimer = window.setTimeout(() => {
      confirmingDeleteSlotNo.value = null
      deleteConfirmTimer = null
    }, 3000)
    return
  }
  if (deleteConfirmTimer !== null) {
    window.clearTimeout(deleteConfirmTimer)
    deleteConfirmTimer = null
  }
  confirmingDeleteSlotNo.value = null
  emit('deleteSlot', slotNo)
}

function onCancel() {
  emit('cancel')
}
</script>

<template>
  <div class="picker-page">
    <FlickeringLight />
    <div class="screen-wrap">
    <div class="screen">
      <header class="header">
        <p class="eyebrow label-mono">{{ copy.eyebrow }}</p>
        <h1 class="label-stencil">{{ copy.title }}</h1>
        <p class="mode-note">{{ copy.note }}</p>
      </header>

      <ul class="slot-list">
        <li v-for="slot in slots" :key="slot.slotNo">
          <div
            class="slot-card"
            :class="{
              empty: !slot.occupied,
              impact: impactSlotNo === slot.slotNo,
              inert: isInert(slot),
            }"
            tabindex="0"
            role="button"
            :aria-disabled="isInert(slot)"
            @click="onSlotClick(slot)"
            @keydown.enter="onSlotClick(slot)"
          >
            <div class="slot-left">
              <span class="slot-no label-mono">SLOT {{ slot.slotNo }}</span>
              <span v-if="slot.occupied" class="slot-title label-stencil">
                사건 #{{ slot.gameId }} · {{ slot.day }}일차
                <span v-if="slot.gameStatus === 'FINISHED'" class="badge label-mono">
                  종료됨 · <span class="ending-type">{{ slot.endingType }}</span>
                </span>
              </span>
              <span v-else class="slot-title label-stencil">빈 슬롯</span>
            </div>

            <div class="slot-right">
              <div class="slot-meta label-mono">
                <template v-if="slot.occupied">
                  신뢰도 {{ slot.trustScore }} · {{ formatSavedAt(slot.savedAt as string) }}
                </template>
                <template v-else>
                  {{ mode === 'SAVE' ? '클릭하여 저장' : '기록 없음' }}
                </template>
              </div>
              <button
                v-if="slot.occupied"
                class="slot-delete-btn"
                :class="{ confirming: confirmingDeleteSlotNo === slot.slotNo }"
                :aria-label="`슬롯 ${slot.slotNo} 삭제`"
                @click="onDeleteClick(slot.slotNo, $event)"
              >
                {{ confirmingDeleteSlotNo === slot.slotNo ? '확인?' : '✕' }}
              </button>
            </div>
          </div>
        </li>
      </ul>

      <div class="footer-actions">
        <router-link v-if="showTitleLink" :to="{ name: 'title' }" class="btn-cancel label-stencil">
          ← 타이틀로
        </router-link>
        <button class="btn-cancel label-stencil" @click="onCancel">취소하고 돌아가기</button>
      </div>
    </div>
    </div>
  </div>
</template>

<style scoped>
.picker-page {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: var(--void);
  color: var(--flicker);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.screen-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

.screen {
  width: min(480px, 100%);
}

.header {
  margin-bottom: 18px;
  text-align: center;
}

.eyebrow {
  color: var(--quarantine);
  margin: 0 0 6px;
  font-size: 11px;
}

h1 {
  font-size: 24px;
  letter-spacing: 0.03em;
  margin: 0;
  color: var(--paper);
}

.mode-note {
  font-size: 13px;
  color: rgba(232, 222, 196, 0.55);
  margin-top: 8px;
  line-height: 1.6;
}

.slot-list {
  list-style: none;
  padding: 0;
  margin: 20px 0 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.slot-card {
  position: relative;
  background: var(--paper);
  color: var(--ink);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 2px;
  transition:
    transform 0.1s ease,
    border-color 0.1s ease;
}

.slot-card:hover:not(.inert) {
  border-color: var(--quarantine);
}

.slot-card:focus-visible {
  outline: 3px solid var(--flicker);
  outline-offset: 2px;
}

.slot-card.inert {
  cursor: default;
}

.slot-card.empty {
  background: transparent;
  border: 2px dashed rgba(232, 222, 196, 0.25);
  color: rgba(232, 222, 196, 0.45);
}

.slot-card.empty:hover:not(.inert) {
  border-color: var(--flicker);
  color: var(--flicker);
}

.slot-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
}

.slot-no {
  font-size: 12px;
  background: rgba(33, 29, 24, 0.08);
  padding: 2px 8px;
  flex-shrink: 0;
}

.slot-card.empty .slot-no {
  background: rgba(232, 222, 196, 0.08);
}

.slot-title {
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.badge {
  font-size: 10px;
  padding: 1px 6px;
  margin-left: 8px;
  background: var(--stamp-red);
  color: var(--paper);
}

.badge .ending-type {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.slot-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.slot-meta {
  font-size: 11px;
  color: rgba(33, 29, 24, 0.6);
  white-space: nowrap;
}

.slot-card.empty .slot-meta {
  color: rgba(232, 222, 196, 0.4);
}

.slot-delete-btn {
  flex-shrink: 0;
  border: none;
  border-left: 1px dashed var(--stamp-red);
  background: transparent;
  color: var(--stamp-red);
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0 0 0 10px;
}

.slot-delete-btn:hover {
  color: var(--paper);
  background: var(--stamp-red);
  padding-right: 4px;
}

.slot-delete-btn:focus-visible {
  outline: 2px solid var(--flicker);
  outline-offset: 2px;
}

.slot-delete-btn.confirming {
  background: var(--stamp-red);
  color: var(--paper);
  padding-right: 4px;
}

.slot-card.impact {
  animation: stamp-hit 0.28s ease;
}

.footer-actions {
  margin-top: 22px;
  display: flex;
  justify-content: center;
  gap: 1.25rem;
}

.btn-cancel {
  font-size: 13px;
  background: none;
  border: none;
  color: rgba(232, 222, 196, 0.5);
  cursor: pointer;
  text-decoration: underline;
}

.btn-cancel:hover {
  color: var(--flicker);
}

@keyframes stamp-hit {
  0% {
    transform: scale(1);
    filter: blur(0);
  }
  35% {
    transform: scale(1.03);
    filter: blur(1px);
  }
  100% {
    transform: scale(1);
    filter: blur(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .slot-card.impact {
    animation: none;
  }
}
</style>
