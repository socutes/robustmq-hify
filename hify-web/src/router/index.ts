import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/provider',
    },
    {
      path: '/provider',
      component: () => import('@/views/provider/ProviderList.vue'),
    },
    {
      path: '/agent',
      component: () => import('@/views/agent/AgentList.vue'),
    },
    {
      path: '/chat',
      component: () => import('@/views/chat/ChatView.vue'),
    },
  ],
})

export default router
