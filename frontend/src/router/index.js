import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/Home.vue'),
    meta: { title: 'AI 应用集合' }
  },
  {
    path: '/love',
    name: 'love',
    component: () => import('../views/LoveApp.vue'),
    meta: { title: 'AI 恋爱大师' }
  },
  {
    path: '/manus',
    name: 'manus',
    component: () => import('../views/ManusApp.vue'),
    meta: { title: 'AI 超级智能体' }
  },
  // 兜底：未匹配路由回到主页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

// 切换页面时同步浏览器标签标题
router.afterEach((to) => {
  if (to.meta?.title) {
    document.title = to.meta.title
  }
})

export default router
