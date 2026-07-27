<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteSaveSlot, listSaveSlots, loadGame } from '../api/games'
import { useAuthStore } from '../stores/auth'
import SlotPicker from '../components/SlotPicker.vue'
import type { SaveSlot } from '../types'

const auth = useAuthStore()
const router = useRouter()

const slots = ref<SaveSlot[]>([])
const loading = ref(true)
const loadingSlotNo = ref<number | null>(null)
const errorMessage = ref('')

async function loadSlots() {
  loading.value = true
  errorMessage.value = ''
  try {
    slots.value = await listSaveSlots(auth.userId as number)
  } catch {
    errorMessage.value = '저장 슬롯을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

// 타이틀에서 곧장 들어온 사건 이어하기 화면에서는 슬롯을 클릭하면 별도 확인 없이 바로 이어한다.
async function onSelectSlot(slotNo: number) {
  const slot = slots.value.find((s) => s.slotNo === slotNo)
  if (!slot || !slot.occupied || slot.gameId == null) {
    return
  }
  loadingSlotNo.value = slotNo
  errorMessage.value = ''
  try {
    const summary = await loadGame(slot.gameId, slot.slotNo)
    router.push({ name: 'game-play', params: { gameId: summary.gameId } })
  } catch {
    errorMessage.value = '불러오기에 실패했습니다.'
    loadingSlotNo.value = null
  }
}

async function onDeleteSlot(slotNo: number) {
  errorMessage.value = ''
  try {
    await deleteSaveSlot(auth.userId as number, slotNo)
    await loadSlots()
  } catch {
    errorMessage.value = '슬롯을 삭제하지 못했습니다.'
  }
}

function goToTitle() {
  router.push({ name: 'title' })
}

onMounted(loadSlots)
</script>

<template>
  <div class="registry-page">
    <p v-if="loading" class="loading-line label-mono">보관소 열람 중...</p>
    <p v-else-if="errorMessage" class="error label-mono">{{ errorMessage }}</p>

    <SlotPicker
      v-else
      :slots="slots"
      mode="LOAD"
      entry-context="FROM_TITLE"
      :loading-slot-no="loadingSlotNo"
      @select="onSelectSlot"
      @delete-slot="onDeleteSlot"
      @back-to-title="goToTitle"
    />
  </div>
</template>

<style scoped>
.registry-page {
  min-height: 100vh;
  background: var(--void);
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-line {
  color: var(--paper);
  opacity: 0.75;
}

.error {
  color: var(--stamp-red);
}
</style>
