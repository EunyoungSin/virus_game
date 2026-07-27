<script setup lang="ts">
import axios from 'axios'
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { deleteSaveSlot, getSummary, listSaveSlots, loadGame, saveGame, sendHeartbeat } from '../api/games'
import { ask, getHistory } from '../api/conversations'
import { decide } from '../api/decisions'
import { nextVisitor, useTestKit } from '../api/visitors'
import { useAuthStore } from '../stores/auth'
import { FINAL_DAY } from '../content/story'
import ConfirmModal from '../components/ConfirmModal.vue'
import DayTransitionOverlay from '../components/DayTransitionOverlay.vue'
import FlickeringLight from '../components/FlickeringLight.vue'
import IntroBriefing from '../components/IntroBriefing.vue'
import SlotPicker from '../components/SlotPicker.vue'
import StampOverlay from '../components/StampOverlay.vue'
import TestKitSlip from '../components/TestKitSlip.vue'
import type {
  ConfirmModalConfig,
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
const transcriptEl = ref<HTMLDivElement | null>(null)
const question = ref('')
const topicTag = ref<TopicTag | ''>('')
const feedback = ref<'correct' | 'incorrect' | null>(null)
const dossierTilt = ref(0)
const stampDecision = ref<Decision | null>(null)

const loadingVisitor = ref(true)
// 일자 전환 3단계(다음 날 도입 문구) 동안 다음 방문자를 미리 인계받아 흐릿하게(투명도
// 15~20%) 비춰 보여주는 중인지. 도입 문구를 클릭하는 순간(onDayTransitionDone) 해제되어
// 또렷하게 페이드인한다.
const visitorFaint = ref(false)
const asking = ref(false)
const deciding = ref(false)
const usingTestKit = ref(false)
const testKitResult = ref<TestKitResult | null>(null)
// 슬립(결과지)이 화면에 떠 있는 동안만 true — 사라진 뒤에도 testKitResult 자체는 남아
// 방문자 카드에 "검사 완료" 영구 태그를 계속 띄운다(둘을 분리해야 영구 태그가 슬립의
// 등장/퇴장 타이밍에 종속되지 않는다).
const showTestKitSlip = ref(false)
// KIT 카운터가 도장 모션과 무관하게 담백하게 살짝 흔들리는 효과 트리거(값이 바뀔 때마다 재생).
const kitPulseToken = ref(0)
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
const savingSlotNo = ref<number | null>(null)

// 불러오기: "저장" 옆의 독립 버튼. "목록으로"에 더 이상 종속되지 않으며, 현재 플레이
// 중인 게임으로 한정하지 않고 유저의 슬롯 5개 전체(GLOBAL)를 보여준다.
const showLoadPicker = ref(false)
const loadSlots = ref<SaveSlot[]>([])
const loadPickerLoading = ref(false)
const loadingSlotNo = ref<number | null>(null)

// 저장 덮어쓰기/슬롯 불러오기 확인 등 모든 확인 모달은 이 하나의
// config로 <ConfirmModal />을 채워 쓴다 — 동시에 두 개가 뜰 일이 없으므로 슬롯 하나로 충분하다.
const activeConfirm = ref<ConfirmModalConfig | null>(null)

let resolveStampDone: (() => void) | null = null
let resolveDayTransitionDone: (() => void) | null = null

// 이 게임 화면이 마운트되어 있는 동안에는(탭이 백그라운드에 있어도) 30초 간격으로
// 하트비트를 보낸다(유휴 타임아웃 판정용) — document.visibilityState는 더 이상 조건으로
// 쓰지 않는다. 화면이 언마운트될 때만(탭을 닫거나 다른 라우트로 이동) 전송이 멈춘다.
// 브라우저가 백그라운드 탭의 타이머를 스로틀링해 정확히 30초 간격이 안 지켜질 수 있지만,
// 서버는 간격이 아니라 last_action_at 기준 누적 시간으로 판단하므로 문제되지 않는다.
const HEARTBEAT_INTERVAL_MS = 30_000
let heartbeatTimer: number | null = null

async function sendHeartbeatOnce() {
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
    showTestKitSlip.value = false
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
    await nextTick()
    if (transcriptEl.value) {
      transcriptEl.value.scrollTop = transcriptEl.value.scrollHeight
    }
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

// 일자 전환 3단계 진입 시점 — 다음 방문자를 미리 인계받아 흐릿한 실루엣으로 보여준다.
// 아직 확정되지 않은 존재라는 뉘앙스이며, 도입 문구를 클릭하기 전까지는 상호작용할
// 수 없다(inert).
function handleEnterIntro() {
  visitorFaint.value = true
  loadNextVisitor()
}

function onDayTransitionDone() {
  dayTransition.value = null
  visitorFaint.value = false
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
      dayLog.value = []
      trustAtDayStart.value = result.game.trustScore
      // 다음 방문자는 3단계(다음 날 도입 문구) 진입 시점(@enter-intro → handleEnterIntro)에
      // 이미 미리 인계받아 흐릿하게 보여준다 — 도입 문구를 클릭(done)하면 또렷해질 뿐이므로
      // 여기서 다시 불러올 필요가 없다.
      await new Promise<void>((resolve) => {
        resolveDayTransitionDone = resolve
      })
      return
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
    kitPulseToken.value++
    showTestKitSlip.value = true
  } catch (error) {
    errorMessage.value =
      axios.isAxiosError(error) && error.response?.status === 409
        ? '검사키트가 남아있지 않습니다.'
        : '검사키트 사용에 실패했습니다.'
  } finally {
    usingTestKit.value = false
  }
}

function onTestKitSlipDone() {
  showTestKitSlip.value = false
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
  if (slot.gameId === gameId) {
    activeConfirm.value = {
      eyebrow: '슬롯 덮어쓰기 승인 요청',
      title: '이 시점을 덮어씁니다',
      body: `슬롯 ${slot.slotNo}의 기존 기록을 현재 진행 상태로 덮어씁니다.`,
      warning: '계속하시겠습니까?',
      watermark: '슬롯 갱신',
      watermarkVariant: 'quarantine',
      confirmLabel: '덮어쓰기',
      onConfirm: () => {
        activeConfirm.value = null
        performSave(slot.slotNo)
      },
      onCancel: () => {
        activeConfirm.value = null
      },
    }
    return
  }
  if (slot.gameId == null || slot.day == null || !slot.gameStatus) {
    return
  }
  activeConfirm.value = {
    eyebrow: '슬롯 덮어쓰기 승인 요청',
    title: '다른 사건의 기록을 덮어씁니다',
    tag: `SLOT ${slot.slotNo} · 사건 #${slot.gameId} (${slot.day}일차${slot.gameStatus === 'FINISHED' ? ' · 종료됨' : ''})`,
    body: '이 슬롯에는 다른 사건의 기록이 있습니다. 덮어쓰면 그 기록은 사라집니다.',
    warning: '정말 덮어쓰시겠습니까?',
    watermark: '슬롯 충돌',
    watermarkVariant: 'stampRed',
    confirmLabel: '덮어쓰기',
    onConfirm: () => {
      activeConfirm.value = null
      performSave(slot.slotNo)
    },
    onCancel: () => {
      activeConfirm.value = null
    },
  }
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

// --- 목록으로: 확인 없이 곧장 타이틀 화면으로 ---
// 어떤 API도 호출하지 않는다 — 라이브 게임 상태(games/visitors/conversations)는
// 그대로 둔 채 화면만 이동한다.
function goToTitle() {
  router.push({ name: 'title' })
}

// --- 불러오기: "저장" 옆의 독립 버튼. 현재 게임으로 한정하지 않고 유저의 슬롯 5개
// 전체(GLOBAL)를 보여준다 — 다른 게임의 시점을 선택해도 정상 동작한다. ---
async function openLoadPicker() {
  errorMessage.value = ''
  loadPickerLoading.value = true
  try {
    loadSlots.value = await listSaveSlots(auth.userId as number)
    showLoadPicker.value = true
  } catch {
    errorMessage.value = '저장 슬롯을 불러오지 못했습니다.'
  } finally {
    loadPickerLoading.value = false
  }
}

// "취소하고 돌아가기" — 어떤 API도 호출하지 않고 슬롯 화면만 닫아 원래 진행 중이던
// 게임 화면으로 복귀한다(라이브 상태 변경 없음).
function cancelLoadPicker() {
  showLoadPicker.value = false
}

function onLoadSlotSelect(slotNo: number) {
  const slot = loadSlots.value.find((s) => s.slotNo === slotNo)
  if (!slot || !slot.occupied || slot.gameId == null) {
    return
  }
  activeConfirm.value = {
    eyebrow: '기록 복원 요청',
    title: '이 시점으로 되돌리기',
    tag: `SLOT ${slot.slotNo} · 사건 #${slot.gameId}${slot.day != null ? ` (${slot.day}일차)` : ''}`,
    body: '이 시점으로 되돌립니다. 이후 진행은 사라집니다.',
    warning: '계속하시겠습니까?',
    watermark: '기록 정정',
    watermarkVariant: 'quarantine',
    confirmLabel: '이어하기',
    onConfirm: () => {
      activeConfirm.value = null
      performLoad(slot)
    },
    onCancel: () => {
      activeConfirm.value = null
    },
  }
}

async function performLoad(slot: SaveSlot) {
  if (slot.gameId == null) {
    return
  }
  loadingSlotNo.value = slot.slotNo
  errorMessage.value = ''
  try {
    const loadedSummary = await loadGame(slot.gameId, slot.slotNo)
    if (loadedSummary.gameId === gameId) {
      // 지금 플레이 중인 게임의 다른 시점으로 되돌린 경우 — gameId가 같아 라우팅으로는
      // 화면이 새로고침되지 않으므로, 이 화면의 상태를 직접 되돌린 시점 기준으로 갱신한다.
      showLoadPicker.value = false
      summary.value = loadedSummary
      dayLog.value = []
      trustAtDayStart.value = loadedSummary.trustScore
      feedback.value = null
      testKitResult.value = null
    showTestKitSlip.value = false
      await loadNextVisitor()
    } else {
      router.push({ name: 'game-play', params: { gameId: loadedSummary.gameId } })
    }
  } catch {
    errorMessage.value = '불러오기에 실패했습니다.'
  } finally {
    loadingSlotNo.value = null
  }
}

async function onLoadSlotDelete(slotNo: number) {
  errorMessage.value = ''
  try {
    await deleteSaveSlot(auth.userId as number, slotNo)
    loadSlots.value = await listSaveSlots(auth.userId as number)
  } catch {
    errorMessage.value = '슬롯을 삭제하지 못했습니다.'
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
  startHeartbeat()
})

onUnmounted(() => {
  stopHeartbeat()
})
</script>

<template>
  <div class="checkpoint" :style="{ '--vignette-alpha': vignetteAlpha }">
    <FlickeringLight :paused="lightPaused" :burst-token="lightBurstToken" />

    <header class="desk-header">
      <button class="link label-stencil" @click="goToTitle">← 목록으로</button>
      <div v-if="summary" class="desk-readout label-mono">
        <span>DAY {{ summary.currentDay }}</span>
        <span>TRUST {{ summary.trustScore }}</span>
        <span>PROCESSED {{ summary.totalProcessed }}</span>
        <span :key="kitPulseToken" class="kit-pulse">KIT {{ summary.testKitsRemaining }}</span>
      </div>
    </header>

    <div class="checkpoint-row">
      <button class="label-stencil" :disabled="savePickerLoading" @click="openSavePicker">
        {{ savePickerLoading ? '슬롯 확인 중...' : '저장' }}
      </button>
      <button class="label-stencil" :disabled="loadPickerLoading" @click="openLoadPicker">
        {{ loadPickerLoading ? '슬롯 확인 중...' : '불러오기' }}
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

    <div v-else-if="visitor" class="desk" :class="{ faint: visitorFaint }" :inert="visitorFaint">
      <article class="dossier" :style="{ transform: `rotate(${dossierTilt}deg)` }">
        <div class="dossier-clip" aria-hidden="true"></div>
        <TestKitSlip
          v-if="showTestKitSlip && testKitResult"
          :infected="testKitResult.infected"
          @done="onTestKitSlipDone"
        />
        <h2 class="dossier-name">
          {{ visitor.name }} <span class="label-mono">({{ visitor.age }})</span>
          <span
            v-if="testKitResult"
            class="test-kit-tag label-mono"
            :class="testKitResult.infected ? 'positive' : 'negative'"
          >
            검사 완료: {{ testKitResult.infected ? '양성' : '음성' }}
          </span>
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

        <div ref="transcriptEl" class="transcript dialogue-scroll">
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

      <form id="askForm" class="ask-form" @submit.prevent="submitQuestion">
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
      </form>

      <div class="action-row">
        <button type="submit" form="askForm" class="submit-btn label-stencil" :disabled="asking || !question.trim()">
          {{ asking ? '질문 전달 중...' : '질문하기' }}
        </button>
        <button
          type="button"
          class="test-kit-btn label-stencil"
          :disabled="usingTestKit || !!testKitResult || (summary?.testKitsRemaining ?? 0) <= 0"
          @click="handleUseTestKit"
        >
          {{ usingTestKit ? '검사 중...' : '검사키트 사용' }}
        </button>
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
      @done="onDayTransitionDone"
      @enter-intro="handleEnterIntro"
      @freeze-light="(v) => (lightPaused = v)"
      @burst-light="lightBurstToken++"
    />

    <SlotPicker
      v-if="showSavePicker"
      :slots="saveSlots"
      mode="SAVE"
      :loading-slot-no="savingSlotNo"
      @select="onSaveSlotSelect"
      @delete-slot="onSaveSlotDelete"
      @cancel="cancelSavePicker"
    />
    <SlotPicker
      v-if="showLoadPicker"
      :slots="loadSlots"
      mode="LOAD"
      entry-context="FROM_IN_GAME"
      :loading-slot-no="loadingSlotNo"
      @select="onLoadSlotSelect"
      @delete-slot="onLoadSlotDelete"
      @cancel="cancelLoadPicker"
      @back-to-title="goToTitle"
    />
    <ConfirmModal v-if="activeConfirm" :config="activeConfirm" />
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

/* 검사키트 사용으로 자원이 줄었을 때 담백하게 살짝 흔들리는 정도만 — 판정 도장의
   임팩트/화면 흔들림과는 전혀 다른, 훨씬 절제된 반응이어야 한다. */
.kit-pulse {
  display: inline-block;
  animation: kit-tick 150ms ease-out;
}

@keyframes kit-tick {
  0% {
    transform: scale(1);
  }
  40% {
    transform: scale(1.18);
  }
  100% {
    transform: scale(1);
  }
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
  opacity: 1;
  filter: blur(0);
  transition:
    opacity 350ms ease,
    filter 350ms ease;
}

/* 일자 전환 3단계(다음 날 도입 문구) 동안 다음 방문자를 흐릿한 실루엣으로만 보여준다 —
   아직 확정되지 않은 존재라는 뉘앙스. 도입 문구를 클릭하면 또렷하게 페이드인한다. */
.desk.faint {
  opacity: 0.18;
  filter: blur(2px);
}

@media (prefers-reduced-motion: reduce) {
  .desk {
    transition: none;
  }
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

/* 대화 로그가 길어질 때 기본 브라우저 스크롤바 대신 서류철 톤의 각진 스크롤바를 쓴다 —
   paper(밝은) 배경 위라 ink 색 손잡이를 쓰고, 둥근 모서리는 이 톤과 안 맞아 쓰지 않는다. */
.dialogue-scroll::-webkit-scrollbar {
  width: 8px;
}

.dialogue-scroll::-webkit-scrollbar-track {
  background: rgba(33, 29, 24, 0.08);
}

.dialogue-scroll::-webkit-scrollbar-thumb {
  background: var(--ink);
  border-radius: 0;
}

.dialogue-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--quarantine);
}

.dialogue-scroll {
  scrollbar-width: thin;
  scrollbar-color: var(--ink) rgba(33, 29, 24, 0.08);
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

.action-row {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  flex-wrap: wrap;
}

.submit-btn {
  background: var(--ink);
  color: var(--paper);
  border: none;
  padding: 0.65rem 1.25rem;
  border-radius: 2px;
}

.test-kit-btn {
  padding: 0.55rem 1rem;
  background: transparent;
  color: var(--quarantine);
  border: 2px solid var(--quarantine);
  border-radius: 3px;
  font-size: 0.85rem;
}

/* 방문자 카드 한 켠에 남는 영구 태그 — 이 방문자를 검사했었다는 사실 자체를
   잊지 않도록, 슬립이 사라진 뒤에도 계속 표시된다. */
.test-kit-tag {
  display: inline-block;
  margin-left: 0.6rem;
  font-size: 0.68rem;
  padding: 0.1rem 0.4rem;
  border: 1px solid currentColor;
  border-radius: 2px;
  vertical-align: middle;
}

.test-kit-tag.negative {
  color: var(--quarantine);
}

.test-kit-tag.positive {
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
