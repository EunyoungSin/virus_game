<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import FlickeringLight from '../components/FlickeringLight.vue'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')

async function startAsGuest() {
  loading.value = true
  errorMessage.value = ''
  try {
    await auth.guestLogin()
    router.push({ name: 'title' })
  } catch {
    errorMessage.value = '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="gate">
    <FlickeringLight />
    <section class="login">
      <p class="eyebrow label-mono">CHECKPOINT ACCESS</p>
      <h1 class="label-stencil">검문소</h1>
      <p class="lede">
        바이러스 봉쇄 도시의 출입관리소 직원이 되어, 방문자와의 대화를 통해 감염 여부를 추리하고
        출입을 허가하거나 거부하십시오. 판정 하나가 도시의 존폐를 가릅니다.
      </p>
      <button class="label-stencil" :disabled="loading" @click="startAsGuest">
        {{ loading ? '접속 중...' : '게스트로 근무 시작' }}
      </button>
      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

      <p class="notice">
        이 게임 기록은 현재 브라우저에만 저장됩니다. 계정 전환 기능이 없으므로, 다른 기기나
        브라우저에서는 이전 진행 상황에 접근할 수 없습니다.
      </p>
      <p class="notice">
        첫 접속 시 서버가 깨어나는 데 최대 1~2분 걸릴 수 있습니다.
      </p>
    </section>
  </div>
</template>

<style scoped>
.gate {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.login {
  max-width: 480px;
  margin: 3rem auto;
  padding: 0 1.25rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.eyebrow {
  color: var(--flicker);
  opacity: 0.75;
  font-size: 0.75rem;
  margin: 0;
}

h1 {
  margin: 0;
  font-size: 2.4rem;
  letter-spacing: 0.08em;
  color: var(--paper);
}

.lede {
  color: var(--paper);
  opacity: 0.85;
  line-height: 1.6;
}

button {
  padding: 0.85rem 1.5rem;
  font-size: 1rem;
  background: var(--paper);
  color: var(--ink);
  border: 3px double var(--ink);
  border-radius: 4px;
}

.error {
  color: var(--stamp-red);
}

.notice {
  color: var(--paper);
  opacity: 0.6;
  font-size: 0.8rem;
  line-height: 1.5;
  margin: 0;
}
</style>
