<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteSaveSlot, getSummary, listSaveSlots, loadGame, saveGame, sendHeartbeat } from '../api/games'
import { ask, getHistory } from '../api/conversations'
import { decide } from '../api/decisions'
import { nextVisitor, useTestKit } from '../api/visitors'
import { useAuthStore } from '../stores/auth'
import { FINAL_DAY } from '../content/story'
import DayTransitionOverlay from '../components/DayTransitionOverlay.vue'
import ExitToListConfirmModal from '../components/ExitToListConfirmModal.vue'
import FlickeringLight from '../components/FlickeringLight.vue'
import IntroBriefing from '../components/IntroBriefing.vue'
import SimpleConfirmModal from '../components/SimpleConfirmModal.vue'
import SlotOverwriteConfirmModal from '../components/SlotOverwriteConfirmModal.vue'
import SlotPicker from '../components/SlotPicker.vue'
import StampOverlay from '../components/StampOverlay.vue'
import type {
  ConversationTurn,
  Decision,
  GameSummary,
  SaveSlot,
  TestKitResult,
  TopicTag,
  Visitor,
} from '../types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const gameId = Number(route.params.gameId)

const TOPIC_LABELS: Record<TopicTag, string> = {
  TRAVEL: '이동 경로',
  JOB: '직업',
  CONTACT: '접촉자',
  SYMPTOM: '증상',
  OTHER: '기타',
}

// 감염과 무관하게 말이 얼버무려지는 순간을 짧게 흔들어 보여주기 위한 휴리스틱.
const HESITATION_PATTERN = /(\.{2,}|…|아[,.]|그[,.]|음[,.]|저[,.]{1,2})/

const summary = ref<GameSummary | null>(null)
const visitor = ref<Visitor | null>(null)
const conversation = ref<ConversationTurn[]>([])
const question = ref('')
const topicTag = ref<TopicTag | ''>('')
const feedback = ref<'correct' | 'incorrect' | null>(null)
const dossierTilt = ref(0)
const stampDecision = ref<Decision | null>(null)

const loadingVisitor = ref(true)
const asking = ref(false)
const deciding = ref(false)
const usingTestKit = ref(false)
const testKitResult = ref<TestKitResult | null>(null)
const errorMessage = ref('')
const checkpointMessage = ref('')

// 하루가 끝날 때 "N일차 종료" 연출에 쓸 통계. 오늘 판정한 것만 쌓아두고 날이 바뀌면 비운다.
const dayLog = ref<{ decision: Decision; correct: boolean }[]>([])
const trustAtDayStart = ref<number | null>(null)
const dayTransition = ref<{
  dayEnded: number
  nextDay: number | null
  processedToday: number
  infectedAdmittedToday: number
  trustBefore: number
  trustAfter: number
  infectedAdmittedSoFar: number
  isFinalDay: boolean
} | null>(null)
const lightPaused = ref(false)
const lightBurstToken = ref(0)
const showIntro = ref(false)

// 저장: 항상 GLOBAL scope 슬롯 선택 화면을 거친다(게임 단위 체크포인트 1개가 아니라 유저 전역 슬롯 5개).
const showSavePicker = ref(false)
const saveSlots = ref<SaveSlot[]>([])
const savePickerLoading = ref(false)
const pendingSaveTarget = ref<SaveSlot | null>(null)
const savingSlotNo = ref<number | null>(null)
const pendingSaveIsSameGame = computed(() => pendingSaveTarget.value?.gameId === gameId)

// 목록으로 나가기: 이 게임을 가리키는 슬롯이 있으면 CURRENT_GAME scope 선택 화면을,
// 없으면 기존 "저장하지 않고 나가기" 확인 모달을 보여준다(둘 다 결국 /games로 이동).
const showExitConfirm = ref(false)
const showExitPicker = ref(false)
const exitPickerLoading = ref(false)
const exitSlots = ref<SaveSlot[]>([])
const pendingExitLoadSlot = ref<SaveSlot | null>(null)
const exitingToList = ref(false)

let resolveStampDone: (() => void) | null = null
let resolveDayTransitionDone: (() => void) | null = null

// 게임 화면이 실제로 열려있는 동안에만 30초 간격으로 하트비트를 보낸다(유휴 타임아웃 판정용).
// 탭이 백그라운드로 전환되면 전송을 멈춘다 — 그 시간은 유휴로 카운트되지 않는다.
const HEARTBEAT_INTERVAL_MS = 30_000
let heartbeatTimer: number | null = null

async function sendHeartbeatOnce() {
  if (document.visibilityState !== 'visible') {
    return
  }
  try {
    const result = await sendHeartbeat(gameId)
    if (result.status === 'FINISHED') {
      stopHeartbeat()
      router.push({ name: 'game-result', params: { gameId } })
    }
  } catch {
    // 하트비트 전송 실패는 게임 진행을 막지 않는다.
  }
}

function startHeartbeat() {
  if (heartbeatTimer !== null) {
    return
  }
  heartbeatTimer = window.setInterval(sendHeartbeatOnce, HEARTBEAT_INTERVAL_MS)
}

function stopHeartbeat() {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    startHeartbeat()
  } else {
    stopHeartbeat()
  }
}

const vignetteAlpha = computed(() => {
  if (!summary.value) return 0
  const trust = Math.max(0, Math.min(100, summary.value.trustScore))
  return (((100 - trust) / 100) * 0.7).toFixed(2)
})

function isHesitant(answer: string): boolean {
  return HESITATION_PATTERN.test(answer)
}

async function loadNextVisitor() {
  loadingVisitor.value = true
  errorMessage.value = ''
  try {
    visitor.value = await nextVisitor(gameId)
    dossierTilt.value = Math.random() * 3 - 1.5
    question.value = ''
    topicTag.value = ''
    testKitResult.value = null
    try {
      conversation.value = await getHistory(gameId, visitor.value.visitorId)
    } catch {
      conversation.value = []
    }
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      router.push({ name: 'game-result', params: { gameId } })
      return
    }
    errorMessage.value = '다음 서류를 인계받지 못했습니다.'
  } finally {
    loadingVisitor.value = false
  }
}

async function refreshSummary() {
  try {
    summary.value = await getSummary(gameId)
  } catch {
    // 요약 갱신 실패는 화면 진행을 막지 않는다.
  }
}

async function submitQuestion() {
  if (!visitor.value || !question.value.trim()) {
    return
  }
  asking.value = true
  errorMessage.value = ''
  const askedQuestion = question.value.trim()
  const askedTopic = topicTag.value || null
  try {
    const response = await ask(gameId, visitor.value.visitorId, askedQuestion, askedTopic)
    conversation.value.push({
      turnNo: response.turnNo,
      question: askedQuestion,
      answer: response.answer,
      topicTag: askedTopic,
    })
    question.value = ''
  } catch {
    errorMessage.value = 'AI 응답을 받지 못했습니다. 다시 시도해주세요.'
  } finally {
    asking.value = false
  }
}

function onStampDone() {
  stampDecision.value = null
  resolveStampDone?.()
  resolveStampDone = null
}

function onDayTransitionDone() {
  dayTransition.value = null
  resolveDayTransitionDone?.()
  resolveDayTransitionDone = null
}

async function submitDecision(decision: Decision) {
  if (!visitor.value) {
    return
  }
  deciding.value = true
  errorMessage.value = ''
  feedback.value = null
  const dayBefore = summary.value?.currentDay ?? 1
  stampDecision.value = decision
  const stampDone = new Promise<void>((resolve) => {
    resolveStampDone = resolve
  })
  try {
    const [result] = await Promise.all([decide(gameId, visitor.value.visitorId, decision), stampDone])
    summary.value = result.game
    dayLog.value.push({ decision, correct: result.correct })

    if (result.endingType) {
      // 마지막 날의 "서류철 닫힘"만 재생하고(오늘 요약 + 4일차 전용 일지), "다음 날 열림" 단계
      // 없이 곧바로 결과 화면으로 넘어간다.
      dayTransition.value = {
        dayEnded: dayBefore,
        nextDay: null,
        processedToday: dayLog.value.length,
        infectedAdmittedToday: dayLog.value.filter((d) => d.decision === 'ADMIT' && !d.correct).length,
        trustBefore: trustAtDayStart.value ?? result.game.trustScore,
        trustAfter: result.game.trustScore,
        infectedAdmittedSoFar: result.game.infectedAdmittedSoFar,
        isFinalDay: dayBefore === FINAL_DAY,
      }
      await new Promise<void>((resolve) => {
        resolveDayTransitionDone = resolve
      })
      router.push({ name: 'game-result', params: { gameId } })
      return
    }

    feedback.value = result.correct ? 'correct' : 'incorrect'

    if (result.game.currentDay > dayBefore) {
      dayTransition.value = {
        dayEnded: dayBefore,
        nextDay: result.game.currentDay,
        processedToday: dayLog.value.length,
        infectedAdmittedToday: dayLog.value.filter((d) => d.decision === 'ADMIT' && !d.correct).length,
        trustBefore: trustAtDayStart.value ?? result.game.trustScore,
        trustAfter: result.game.trustScore,
        infectedAdmittedSoFar: result.game.infectedAdmittedSoFar,
        isFinalDay: false,
      }
      await new Promise<void>((resolve) => {
        resolveDayTransitionDone = resolve
      })
      dayLog.value = []
      trustAtDayStart.value = result.game.trustScore
    }

    await loadNextVisitor()
  } catch (error) {
    stampDecision.value = null
    if (axios.isAxiosError(error) && error.response?.status === 409) {
      errorMessage.value = '이미 처리된 방문자입니다. 다음 방문자를 불러옵니다.'
      await loadNextVisitor()
    } else {
      errorMessage.value = '판정 처리에 실패했습니다.'
    }
  } finally {
    deciding.value = false
  }
}

async function handleUseTestKit() {
  if (!visitor.value) {
    return
  }
  usingTestKit.value = true
  errorMessage.value = ''
  try {
    testKitResult.value = await useTestKit(gameId, visitor.value.visitorId)
    if (summary.value) {
      summary.value = { ...summary.value, testKitsRemaining: testKitResult.value.testKitsRemaining }
    }
  } catch (error) {
    errorMessage.value =
      axios.isAxiosError(error) && error.response?.status === 409
        ? '검사키트가 남아있지 않습니다.'
        : '검사키트 사용에 실패했습니다.'
  } finally {
    usingTestKit.value = false
  }
}

// --- 저장: 항상 GLOBAL scope 슬롯 선택 화면을 연다 ---
async function openSavePicker() {
  errorMessage.value = ''
  savePickerLoading.value = true
  try {
    saveSlots.value = await listSaveSlots(auth.userId as number)
    showSavePicker.value = true
  } catch {
    errorMessage.value = '저장 슬롯을 불러오지 못했습니다.'
  } finally {
    savePickerLoading.value = false
  }
}

function cancelSavePicker() {
  showSavePicker.value = false
}

function onSaveSlotSelect(slotNo: number) {
  const slot = saveSlots.value.find((s) => s.slotNo === slotNo)
  if (!slot) {
    return
  }
  if (!slot.occupied) {
    performSave(slotNo)
    return
  }
  pendingSaveTarget.value = slot
}

function cancelSaveOverwrite() {
  pendingSaveTarget.value = null
}

async function confirmSaveOverwrite() {
  const slot = pendingSaveTarget.value
  if (!slot) {
    return
  }
  pendingSaveTarget.value = null
  await performSave(slot.slotNo)
}

async function performSave(slotNo: number) {
  savingSlotNo.value = slotNo
  errorMessage.value = ''
  checkpointMessage.value = ''
  try {
    summary.value = await saveGame(gameId, slotNo)
    checkpointMessage.value = `슬롯 ${slotNo}에 저장했습니다.`
    showSavePicker.value = false
  } catch {
    errorMessage.value = '저장에 실패했습니다.'
  } finally {
    savingSlotNo.value = null
  }
}

async function onSaveSlotDelete(slotNo: number) {
  errorMessage.value = ''
  try {
    await deleteSaveSlot(auth.userId as number, slotNo)
    saveSlots.value = await listSaveSlots(auth.userId as number)
  } catch {
    errorMessage.value = '슬롯을 삭제하지 못했습니다.'
  }
}

// --- 목록으로 나가기 ---
async function requestExitToList() {
  errorMessage.value = ''
  exitPickerLoading.value = true
  try {
    const allSlots = await listSaveSlots(auth.userId as number)
    exitSlots.value = allSlots.filter((s) => s.occupied && s.gameId === gameId)
    if (exitSlots.value.length === 0) {
      showExitConfirm.value = true
    } else {
      showExitPicker.value = true
    }
  } catch {
    errorMessage.value = '저장 슬롯 정보를 불러오지 못했습니다.'
  } finally {
    exitPickerLoading.value = false
  }
}

function cancelExitToList() {
  showExitConfirm.value = false
}

// 이 게임을 가리키는 저장 슬롯이 하나도 없는 경우 — 되돌릴 대상이 없으므로 API 호출 없이 그냥 나간다.
function confirmExitToList() {
  showExitConfirm.value = false
  router.push({ name: 'game-list' })
}

function cancelExitPicker() {
  showExitPicker.value = false
}

function onExitSlotSelect(slotNo: number) {
  const slot = exitSlots.value.find((s) => s.slotNo === slotNo)
  if (!slot) {
    return
  }
  pendingExitLoadSlot.value = slot
}

function cancelExitLoad() {
  pendingExitLoadSlot.value = null
}

async function confirmExitLoad() {
  const slot = pendingExitLoadSlot.value
  if (!slot || slot.gameId == null) {
    return
  }
  exitingToList.value = true
  errorMessage.value = ''
  try {
    await loadGame(slot.gameId, slot.slotNo)
    router.push({ name: 'game-list' })
  } catch {
    errorMessage.value = '불러오기에 실패했습니다.'
    exitingToList.value = false
  } finally {
    pendingExitLoadSlot.value = null
  }
}

async function onExitSlotDelete(slotNo: number) {
  errorMessage.value = ''
  try {
    await deleteSaveSlot(auth.userId as number, slotNo)
    exitSlots.value = exitSlots.value.filter((s) => s.slotNo !== slotNo)
    if (exitSlots.value.length === 0) {
      showExitPicker.value = false
    }
  } catch {
    errorMessage.value = '슬롯을 삭제하지 못했습니다.'
  }
}

// 일자 전환 연출의 "저장하시겠습니까?" 넛지는 슬롯을 고르는 화면이 아니라 빠른 확인 한 번이어야
// 한다(연출 자체가 사용자 클릭을 기다리는 블로킹 게이트라, 그 안에서 또 다른 화면을 열면 흐름이
// 무거워진다). 그래서 슬롯은 자동으로 고른다: 이 게임이 이미 차지한 슬롯이 있으면 그걸 갱신하고,
// 없으면 빈 슬롯 중 하나를 새로 쓴다. 쓸 수 있는 슬롯이 전혀 없으면(5개 전부 다른 게임 차지) 조용히
// 건너뛴다 — 이 넛지 하나 때문에 하루 전환이 막혀서는 안 된다.
async function handleDayTransitionSave() {
  try {
    const slots = await listSaveSlots(auth.userId as number)
    const target = slots.find((s) => s.occupied && s.gameId === gameId) ?? slots.find((s) => !s.occupied)
    if (!target) {
      return
    }
    await performSave(target.slotNo)
  } catch {
    // 저장 실패가 하루 전환 흐름 자체를 막지는 않는다.
  }
}

function dismissIntro() {
  showIntro.value = false
  window.localStorage.setItem(`checkpoint.introShown.${gameId}`, '1')
}

onMounted(async () => {
  await refreshSummary()
  trustAtDayStart.value = summary.value?.trustScore ?? 100
  if (
    summary.value &&
    summary.value.currentDay === 1 &&
    summary.value.totalProcessed === 0 &&
    !window.localStorage.getItem(`checkpoint.introShown.${gameId}`)
  ) {
    showIntro.value = true
  }
  await loadNextVisitor()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  if (document.visibilityState === 'visible') {
    startHeartbeat()
  }
})

onUnmounted(() => {
  stopHeartbeat()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <div class="checkpoint" :style="{ '--vignette-alpha': vignetteAlpha }">
    <FlickeringLight :paused="lightPaused" :burst-token="lightBurstToken" />

    <header class="desk-header">
      <button class="link label-stencil" :disabled="exitPickerLoading" @click="requestExitToList">
        ← 목록으로
      </button>
      <div v-if="summary" class="desk-readout label-mono">
        <span>DAY {{ summary.currentDay }}</span>
        <span>TRUST {{ summary.trustScore }}</span>
        <span>PROCESSED {{ summary.totalProcessed }}</span>
        <span>KIT {{ summary.testKitsRemaining }}</span>
      </div>
    </header>

    <div class="checkpoint-row">
      <button class="label-stencil" :disabled="savePickerLoading" @click="openSavePicker">
        {{ savePickerLoading ? '슬롯 확인 중...' : '저장' }}
      </button>
    </div>
    <p v-if="checkpointMessage" class="checkpoint-message label-mono">{{ checkpointMessage }}</p>

    <p v-if="feedback === 'correct'" class="feedback correct label-mono">
      <span aria-hidden="true">✓</span> 이전 판정: 정확함 (CORRECT)
    </p>
    <p v-if="feedback === 'incorrect'" class="feedback incorrect label-mono">
      <span aria-hidden="true">✕</span> 이전 판정: 오판정 (INCORRECT)
    </p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <p v-if="loadingVisitor" class="loading-line label-mono">다음 서류를 인계받는 중...</p>

    <div v-else-if="visitor" class="desk">
      <article class="dossier" :style="{ transform: `rotate(${dossierTilt}deg)` }">
        <div class="dossier-clip" aria-hidden="true"></div>
        <h2 class="dossier-name">
          {{ visitor.name }} <span class="label-mono">({{ visitor.age }})</span>
        </h2>
        <dl class="dossier-fields">
          <div class="field">
            <dt class="label-stencil">직업</dt>
            <dd>{{ visitor.jobClaimed ?? '미확인' }}</dd>
          </div>
          <div class="field">
            <dt class="label-stencil">출발지</dt>
            <dd>{{ visitor.originCity ?? '미확인' }}</dd>
          </div>
          <div class="field" v-if="visitor.travelHistory && visitor.travelHistory.length">
            <dt class="label-stencil">이동 경로</dt>
            <dd>
              <span v-for="(stop, index) in visitor.travelHistory" :key="index">
                {{ stop.city }}<span class="label-mono">({{ stop.date }})</span
                ><span v-if="index < visitor.travelHistory.length - 1"> → </span>
              </span>
            </dd>
          </div>
        </dl>

        <div class="transcript">
          <p v-if="conversation.length === 0" class="hint">질문지에 적어 방문자를 심문하십시오.</p>
          <div
            v-for="turn in conversation"
            :key="turn.turnNo"
            class="turn"
            :class="{ hesitant: isHesitant(turn.answer) }"
          >
            <p class="question label-stencil">
              Q{{ turn.turnNo }}. {{ turn.question }}
              <span v-if="turn.topicTag" class="tag" :class="{ symptom: turn.topicTag === 'SYMPTOM' }">
                [{{ TOPIC_LABELS[turn.topicTag] }}]
              </span>
            </p>
            <p class="answer">{{ turn.answer }}</p>
          </div>
        </div>
      </article>

      <form class="ask-form" @submit.prevent="submitQuestion">
        <select v-model="topicTag" aria-label="질문 주제">
          <option value="">주제 선택 (선택)</option>
          <option v-for="(label, tag) in TOPIC_LABELS" :key="tag" :value="tag">{{ label }}</option>
        </select>
        <textarea
          v-model="question"
          rows="2"
          placeholder="방문자에게 질문을 입력하십시오"
          :disabled="asking"
        ></textarea>
        <button type="submit" class="label-stencil" :disabled="asking || !question.trim()">
          {{ asking ? '질문 전달 중...' : '질문하기' }}
        </button>
      </form>

      <div class="test-kit-row">
        <button
          class="label-stencil"
          :disabled="usingTestKit || !!testKitResult || (summary?.testKitsRemaining ?? 0) <= 0"
          @click="handleUseTestKit"
        >
          {{ usingTestKit ? '검사 중...' : '검사키트 사용' }}
        </button>
        <p v-if="testKitResult" class="test-kit-result label-mono" :class="{ positive: testKitResult.infected }">
          검사 결과:
          <span aria-hidden="true">{{ testKitResult.infected ? '⚠' : '✓' }}</span>
          {{ testKitResult.infected ? '감염 확정 (POSITIVE)' : '비감염 확정 (NEGATIVE)' }}
        </p>
      </div>

      <div class="decision-buttons">
        <button
          class="stamp-button admit label-stencil"
          :disabled="deciding"
          @click="submitDecision('ADMIT')"
        >
          <span aria-hidden="true">◎</span> 통과 · ADMIT
        </button>
        <button
          class="stamp-button reject label-stencil"
          :disabled="deciding"
          @click="submitDecision('REJECT')"
        >
          <span aria-hidden="true">✕</span> 거부 · REJECT
        </button>
      </div>
    </div>

    <IntroBriefing v-if="showIntro" @dismiss="dismissIntro" />
    <StampOverlay v-if="stampDecision" :decision="stampDecision" @done="onStampDone" />
    <DayTransitionOverlay
      v-if="dayTransition"
      v-bind="dayTransition"
      :request-save="handleDayTransitionSave"
      @done="onDayTransitionDone"
      @freeze-light="(v) => (lightPaused = v)"
      @burst-light="lightBurstToken++"
    />

    <SlotPicker
      v-if="showSavePicker"
      :slots="saveSlots"
      mode="SAVE"
      scope="GLOBAL"
      :loading-slot-no="savingSlotNo"
      @select="onSaveSlotSelect"
      @delete-slot="onSaveSlotDelete"
      @cancel="cancelSavePicker"
    />
    <SimpleConfirmModal
      v-if="pendingSaveTarget && pendingSaveIsSameGame"
      title="이 시점을 덮어씁니다"
      :body="`슬롯 ${pendingSaveTarget.slotNo}의 기존 기록을 현재 진행 상태로 덮어씁니다. 계속하시겠습니까?`"
      confirm-label="덮어쓰기"
      @confirm="confirmSaveOverwrite"
      @cancel="cancelSaveOverwrite"
    />
    <SlotOverwriteConfirmModal
      v-if="pendingSaveTarget && !pendingSaveIsSameGame && pendingSaveTarget.gameId != null && pendingSaveTarget.day != null && pendingSaveTarget.gameStatus"
      :slot-no="pendingSaveTarget.slotNo"
      :game-id="pendingSaveTarget.gameId"
      :day="pendingSaveTarget.day"
      :game-status="pendingSaveTarget.gameStatus"
      @confirm="confirmSaveOverwrite"
      @cancel="cancelSaveOverwrite"
    />

    <ExitToListConfirmModal
      v-if="showExitConfirm"
      @confirm="confirmExitToList"
      @cancel="cancelExitToList"
    />
    <SlotPicker
      v-if="showExitPicker"
      :slots="exitSlots"
      mode="LOAD"
      scope="CURRENT_GAME"
      :loading-slot-no="exitingToList ? pendingExitLoadSlot?.slotNo ?? null : null"
      @select="onExitSlotSelect"
      @delete-slot="onExitSlotDelete"
      @cancel="cancelExitPicker"
    />
    <SimpleConfirmModal
      v-if="pendingExitLoadSlot"
      title="이 시점으로 되돌립니다"
      body="선택한 시점 이후의 판정과 대화는 사라집니다. 계속하시겠습니까?"
      confirm-label="이어하기"
      @confirm="confirmExitLoad"
      @cancel="cancelExitLoad"
    />
  </div>
</template>

<style scoped>
.checkpoint {
  position: relative;
  min-height: 100vh;
  max-width: 720px;
  margin: 0 auto;
  padding: 0 1rem 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.checkpoint::after {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 5;
  pointer-events: none;
  box-shadow: inset 0 0 12vw 4vw rgba(14, 18, 16, var(--vignette-alpha, 0));
  transition: box-shadow 0.6s ease;
}

.desk-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 0.75rem;
}

.link {
  background: none;
  border: none;
  color: var(--flicker);
  text-decoration: underline;
  padding: 0;
  font-size: 0.85rem;
}

.desk-readout {
  display: flex;
  gap: 1rem;
  font-size: 0.85rem;
  color: var(--flicker);
}

.checkpoint-row {
  display: flex;
  gap: 0.6rem;
}

.checkpoint-row button {
  padding: 0.4rem 0.9rem;
  font-size: 0.75rem;
  background: transparent;
  color: var(--flicker);
  border: 1px solid var(--flicker);
  border-radius: 2px;
  opacity: 0.85;
}

.checkpoint-message {
  color: var(--flicker);
  font-size: 0.8rem;
  opacity: 0.85;
  margin: -0.5rem 0 0;
}

.feedback {
  padding: 0.5rem 0.75rem;
  border-radius: 2px;
  font-size: 0.85rem;
  border: 1px solid currentColor;
}

.feedback.correct {
  color: var(--quarantine);
  background: rgba(74, 93, 69, 0.12);
}

.feedback.incorrect {
  color: var(--stamp-red);
  background: rgba(122, 46, 40, 0.12);
}

.error {
  color: var(--stamp-red);
}

.loading-line {
  color: var(--flicker);
  opacity: 0.8;
}

.desk {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.dossier {
  position: relative;
  background: var(--paper);
  color: var(--ink);
  border-radius: 3px;
  padding: 1.75rem 1.5rem 1.25rem;
  box-shadow: var(--shadow-doc);
  transform-origin: top center;
}

.dossier-clip {
  position: absolute;
  top: -14px;
  left: 50%;
  transform: translateX(-50%);
  width: 64px;
  height: 22px;
  background: linear-gradient(180deg, var(--ink) 0%, rgba(33, 29, 24, 0.6) 100%);
  border-radius: 4px;
  box-shadow: 0 3px 6px rgba(0, 0, 0, 0.4);
}

.dossier-name {
  margin: 0 0 1rem;
  font-family: var(--font-serif);
  font-size: 1.4rem;
}

.dossier-fields {
  display: grid;
  gap: 0.6rem;
  margin: 0 0 1.25rem;
}

.field {
  display: grid;
  grid-template-columns: 5.5rem 1fr;
  gap: 0.5rem;
  border-bottom: 1px dashed var(--paper-dark);
  padding-bottom: 0.35rem;
}

.field dt {
  font-size: 0.75rem;
  opacity: 0.75;
}

.field dd {
  margin: 0;
}

.transcript {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-height: 320px;
  overflow-y: auto;
  border-top: 1px solid var(--paper-dark);
  padding-top: 0.75rem;
}

.hint {
  color: var(--ink-soft);
  font-style: italic;
}

.turn {
  border-bottom: 1px solid rgba(33, 29, 24, 0.15);
  padding-bottom: 0.6rem;
}

.turn.hesitant .answer {
  animation: text-jitter 260ms ease-in-out;
}

.question {
  font-size: 0.85rem;
  margin: 0 0 0.3rem;
}

.answer {
  margin: 0;
  line-height: 1.5;
}

.tag {
  font-weight: 400;
  opacity: 0.65;
  font-size: 0.75rem;
}

.tag.symptom {
  color: var(--quarantine);
  opacity: 1;
}

.ask-form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.ask-form select,
.ask-form textarea {
  background: var(--paper);
  color: var(--ink);
  border: 1px solid var(--paper-dark);
  border-radius: 2px;
  padding: 0.6rem;
  font-family: var(--font-serif);
}

.ask-form button {
  align-self: flex-start;
  background: var(--ink);
  color: var(--paper);
  border: none;
  padding: 0.65rem 1.25rem;
  border-radius: 2px;
}

.test-kit-row {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  flex-wrap: wrap;
}

.test-kit-row button {
  padding: 0.55rem 1rem;
  background: transparent;
  color: var(--quarantine);
  border: 2px solid var(--quarantine);
  border-radius: 3px;
  font-size: 0.85rem;
}

.test-kit-result {
  color: var(--quarantine);
  font-size: 0.85rem;
}

.test-kit-result.positive {
  color: var(--stamp-red);
}

.decision-buttons {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.stamp-button {
  flex: 1;
  min-width: 10rem;
  padding: 0.85rem 1rem;
  background: var(--paper);
  border: 3px double currentColor;
  border-radius: 4px;
  font-size: 0.95rem;
}

.stamp-button.admit {
  color: var(--ink);
}

.stamp-button.reject {
  color: var(--stamp-red);
}

@keyframes text-jitter {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-2px);
  }
  50% {
    transform: translateX(2px);
  }
  75% {
    transform: translateX(-1px);
  }
}

@media (max-width: 480px) {
  .field {
    grid-template-columns: 1fr;
    gap: 0.15rem;
  }

  .desk-readout {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .decision-buttons {
    flex-direction: column;
  }

  .stamp-button {
    min-width: 100%;
  }
}
</style>
