import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import TitleView from '../views/TitleView.vue'
import GameListView from '../views/GameListView.vue'
import ArchiveView from '../views/ArchiveView.vue'
import GamePlayView from '../views/GamePlayView.vue'
import ResultView from '../views/ResultView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/title', name: 'title', component: TitleView, meta: { requiresAuth: true } },
    { path: '/games', name: 'game-list', component: GameListView, meta: { requiresAuth: true } },
    { path: '/archive', name: 'archive', component: ArchiveView, meta: { requiresAuth: true } },
    {
      path: '/games/:gameId/play',
      name: 'game-play',
      component: GamePlayView,
      meta: { requiresAuth: true },
    },
    {
      path: '/games/:gameId/result',
      name: 'game-result',
      component: ResultView,
      meta: { requiresAuth: true },
    },
    { path: '/', redirect: '/login' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'title' }
  }
  return true
})

export default router
