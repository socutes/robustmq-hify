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
    {
      path: '/knowledge',
      component: () => import('@/views/knowledge/KnowledgeList.vue'),
    },
    {
      path: '/knowledge/:kbId/documents',
      component: () => import('@/views/knowledge/DocumentList.vue'),
    },
  ],
})

export default router
