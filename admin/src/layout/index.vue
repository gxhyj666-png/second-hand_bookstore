<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <div class="logo-mark">书</div>
        <div v-show="!collapsed" class="logo-text">
          <strong>同窗书</strong>
          <span>校园教材循环平台</span>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="layout-menu"
        background-color="#1b2436"
        text-color="#b9c3d6"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
      <div v-show="!collapsed" class="aside-footer">v1.0 · 毕业设计演示</div>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-button text class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon :size="18"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag type="info" effect="plain" round>{{ userStore.roleLabel }}</el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-trigger">
              <el-avatar :size="30" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" disabled>账号：{{ userStore.user?.nickname || '管理员' }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const menus = [
  { path: '/dashboard', title: '数据看板', icon: 'DataLine' },
  { path: '/textbook', title: '教材库', icon: 'Reading' },
  { path: '/listing', title: '商品审核', icon: 'Goods' },
  { path: '/order', title: '订单管理', icon: 'Tickets' },
  { path: '/user', title: '用户管理', icon: 'User' },
  { path: '/ticket', title: '纠纷工单', icon: 'Service' },
  { path: '/groupbuy', title: '班级接龙', icon: 'Share' }
]

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')
const avatarText = computed(() => (userStore.nickname || '管').slice(0, 1))

async function handleCommand(command) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定退出当前管理员账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (e) {
    return
  }
  userStore.logout()
  router.replace('/login')
}
</script>
