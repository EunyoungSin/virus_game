<script setup lang="ts">
import { computed, ref } from 'vue'
import ConfirmModal from './ConfirmModal.vue'
import FlickeringLight from './FlickeringLight.vue'
import type { ConfirmModalConfig, SaveSlot } from '../types'

// entryContext는 LOAD 모드에서 하단 버튼 구성을 결정한다: 게임 내 "불러오기" 버튼으로
// 들어왔다면 "취소하고 돌아가기"(원래 게임 화면으로) + "타이틀로 돌아가기" 둘 다,
// 타이틀의 "사건 이어하기"로 들어왔다면 돌아갈 게임 화면 자체가 없으므로 "타이틀로
// 돌아가기" 하나만 보여준다. SAVE 모드는 항상 게임 내에서만 열리므로 해당 없음.
const props = withDefaults(
  defineProps<{
    slots: SaveSlot[]
    mode: 'SAVE' | 'LOAD'
    entryContext?: 'FROM_TITLE' | 'FROM_IN_GAME'
    loadingSlotNo?: number | null
  }>(),
  {
    entryContext: 'FROM_IN_GAME',
  },
)

const emit = defineEmits<{
  select: [slotNo: number]
  deleteSlot: [slotNo: number]
  cancel: []
  backToTitle: []
}>()

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const impactSlotNo = ref<number | null>(null)
// 삭제 버튼 클릭 시 config를 채워 ConfirmModal을 띄운다. "확인"을 눌러야만
// emit('deleteSlot')이 나가고, "취소"나 ESC는 config.onCancel()로 모달만 닫는다.
const deleteConfirm = ref<ConfirmModalConfig | null>(null)

const copy = computed(() => {
  if (props.mode === 'SAVE') {
    return {
      eyebrow: '기록 보존 — 슬롯 선택',
      title: '저장할 슬롯을 선택하세요',
      note: '이미 기록이 있는 슬롯을 고르면 덮어씁니다.',
    }
  }
  return {
    eyebrow: '사건 이어하기',
    title: '이어서 진행할 사건을 선택하세요',
    note: '선택한 시점 이후의 판정과 대화는 사라집니다.',
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

function onDeleteClick(slot: SaveSlot, event: MouseEvent) {
  event.stopPropagation()
  if (slot.gameId == null || slot.day == null) {
    return
  }
  deleteConfirm.value = {
    eyebrow: '슬롯 삭제 승인 요청',
    title: '이 슬롯을 삭제합니다',
    tag: `SLOT ${slot.slotNo} · 사건 #${slot.gameId} (${slot.day}일차)`,
    body: '삭제하시겠습니까?',
    watermark: 'SLOT',
    watermarkVariant: 'stampRed',
    confirmLabel: '확인',
    onConfirm: () => {
      emit('deleteSlot', slot.slotNo)
      deleteConfirm.value = null
    },
    onCancel: () => {
      deleteConfirm.value = null
    },
  }
}

function onCancel() {
  emit('cancel')
}

function onBackToTitle() {
  emit('backToTitle')
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
                :aria-label="`슬롯 ${slot.slotNo} 삭제`"
                @click="onDeleteClick(slot, $event)"
              >
                ✕
              </button>
            </div>
          </div>
        </li>
      </ul>

      <div class="footer-actions">
        <template v-if="mode === 'LOAD' && entryContext === 'FROM_TITLE'">
          <button class="btn-cancel label-stencil" @click="onBackToTitle">타이틀로 돌아가기</button>
        </template>
        <template v-else-if="mode === 'LOAD' && entryContext === 'FROM_IN_GAME'">
          <button class="btn-cancel label-stencil" @click="onCancel">취소하고 돌아가기</button>
          <span class="footer-divider">·</span>
          <button class="btn-cancel label-stencil" @click="onBackToTitle">타이틀로 돌아가기</button>
        </template>
        <button v-else class="btn-cancel label-stencil" @click="onCancel">취소하고 돌아가기</button>
      </div>
    </div>
    </div>

    <ConfirmModal v-if="deleteConfirm" :config="deleteConfirm" />
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

.slot-card.impact {
  animation: stamp-hit 0.28s ease;
}

.footer-actions {
  margin-top: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.footer-divider {
  color: rgba(232, 222, 196, 0.3);
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
