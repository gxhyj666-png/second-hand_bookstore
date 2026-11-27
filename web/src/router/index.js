import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 用 hash 路由：① 静态托管不需要 history fallback；② 将来 Capacitor 打包 APK 时本地加载不会 404。
 */
const routes = [
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: '同窗书' } },
  { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue'), meta: { title: '找书' } },
  { path: '/listing/:id', name: 'listing', component: () => import('@/views/ListingDetailView.vue'), meta: { title: '教材详情' } },
  { path: '/publish', name: 'publish', component: () => import('@/views/PublishView.vue'), meta: { title: '发布教材', auth: true } },
  { path: '/orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { title: '我的订单', auth: true } },
  { path: '/order/:orderNo', name: 'order', component: () => import('@/views/OrderDetailView.vue'), meta: { title: '订单详情', auth: true } },
  { path: '/review/:orderNo', name: 'review', component: () => import('@/views/ReviewView.vue'), meta: { title: '发表评价', auth: true } },
  { path: '/mine', name: 'mine', component: () => import('@/views/MineView.vue'), meta: { title: '我的' } },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { title: '注册' } },
  { path: '/verify', name: 'verify', component: () => import('@/views/VerifyStudentView.vue'), meta: { title: '学号认证', auth: true } },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人资料', auth: true } },
  { path: '/favorites', name: 'favorites', component: () => import('@/views/FavoritesView.vue'), meta: { title: '我的收藏', auth: true } },
  { path: '/notices', name: 'notices', component: () => import('@/views/NoticesView.vue'), meta: { title: '消息通知', auth: true } },
  { path: '/credit', name: 'credit', component: () => import('@/views/CreditLogView.vue'), meta: { title: '信用记录', auth: true } },
  { path: '/tickets', name: 'tickets', component: () => import('@/views/TicketsView.vue'), meta: { title: '纠纷工单', auth: true } },
  { path: '/group-buys', name: 'groupBuys', component: () => import('@/views/GroupBuysView.vue'), meta: { title: '班级接龙' } },
  { path: '/group-buys/:inviteCode', name: 'groupBuyDetail', component: () => import('@/views/GroupBuyDetailView.vue'), meta: { title: '接龙详情' } },
  { path: '/pickup', name: 'pickup', component: () => import('@/views/PickupPointsView.vue'), meta: { title: '自提点' } },
  { path: '/courses', name: 'courses', component: () => import('@/views/CoursesView.vue'), meta: { title: '课程教材' } },
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 同窗书` : '同窗书 · 校园教材循环'
  if (to.meta.auth && !localStorage.getItem('tl_token')) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
