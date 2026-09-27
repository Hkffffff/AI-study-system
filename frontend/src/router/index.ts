import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** Milestone that will implement this page (shown on placeholders). */
    milestone?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/home/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'archives',
          name: 'archives',
          component: () => import('@/views/placeholder/ComingSoon.vue'),
          meta: { title: '资料库', milestone: 'M2' },
        },
        {
          path: 'questions',
          name: 'questions',
          component: () => import('@/views/placeholder/ComingSoon.vue'),
          meta: { title: '错题', milestone: 'M3' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/placeholder/ComingSoon.vue'),
          meta: { title: '设置', milestone: 'M1' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home',
    },
  ],
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · AI Study` : 'AI Study'
})

export default router
