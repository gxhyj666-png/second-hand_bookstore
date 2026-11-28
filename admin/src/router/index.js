import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板', icon: 'DataLine' }
      },
      {
        path: 'textbook',
        name: 'Textbook',
        component: () => import('@/views/textbook/index.vue'),
        meta: { title: '教材库', icon: 'Reading' }
      },
      {
        path: 'listing',
        name: 'Listing',
        component: () => import('@/views/listing/index.vue'),
        meta: { title: '商品审核', icon: 'Goods' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理', icon: 'Tickets' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'ticket',
        name: 'Ticket',
        component: () => import('@/views/ticket/index.vue'),
        meta: { title: '纠纷工单', icon: 'Service' }
      },
      {
        path: 'groupbuy',
        name: 'GroupBuy',
        component: () => import('@/views/groupbuy/index.vue'),
        meta: { title: '班级接龙', icon: 'Share' }
      },
      {
        path: 'merchants',
        name: 'Merchant',
        component: () => import('@/views/merchant/Index.vue'),
        meta: { title: '商户审核', icon: 'Shop', group: '平台管理' }
      },
      {
        path: 'refunds',
        name: 'Refund',
        component: () => import('@/views/refund/Index.vue'),
        meta: { title: '纠纷仲裁', icon: 'Warning', group: '平台管理' }
      },
      {
        path: 'upgrade-codes',
        name: 'UpgradeCodes',
        component: () => import('@/views/upgrade/index.vue'),
        meta: { title: '进阶码管理', icon: 'Key', group: '平台管理' }
      },
      {
        path: 'operators',
        name: 'Operators',
        component: () => import('@/views/operator/index.vue'),
        meta: { title: '运营人员', icon: 'Avatar', group: '平台管理' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('ts_admin_token')
  if (to.meta.public) {
    if (token && to.path === '/login') {
      next('/dashboard')
      return
    }
    next()
    return
  }
  if (!token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

router.afterEach((to) => {
  const title = to.meta && to.meta.title
  document.title = title ? `${title} · 同窗书管理后台` : '同窗书 · 管理后台'
})

export default router
