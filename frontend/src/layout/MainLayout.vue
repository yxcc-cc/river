<template>
  <el-container class="main-layout">
    <!-- Mobile overlay -->
    <div v-if="isMobile && showDrawer" class="drawer-overlay" @click="showDrawer = false"></div>

    <!-- Sidebar -->
    <el-aside :width="sidebarWidth" :class="['sidebar', { 'sidebar-drawer': isMobile }]">
      <!-- Logo -->
      <div class="sidebar-logo" :class="{ collapsed: isCollapse && !isMobile }">
        <div class="logo-icon">
          <svg viewBox="0 0 48 48" width="28" height="28" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M24 4L6 12v12c0 11 8 18 18 22 10-4 18-11 18-22V12L24 4z" fill="rgba(56,97,251,0.1)" stroke="url(#sidebarLogoGrad)" stroke-width="2.5" stroke-linejoin="round"/>
            <path d="M14 24c2-2 4-2 6 0s4 2 6 0 4-2 6 0" stroke="#3861fb" stroke-width="2" stroke-linecap="round" fill="none" opacity="0.8"/>
            <path d="M14 29c2-2 4-2 6 0s4 2 6 0 4-2 6 0" stroke="#3861fb" stroke-width="1.5" stroke-linecap="round" fill="none" opacity="0.4"/>
            <circle cx="24" cy="18" r="3" fill="#3861fb"/>
            <defs><linearGradient id="sidebarLogoGrad" x1="6" y1="4" x2="42" y2="38"><stop stop-color="#3861fb"/><stop offset="1" stop-color="#8b5cf6"/></linearGradient></defs>
          </svg>
        </div>
        <transition name="fade">
          <span v-if="!isCollapse || isMobile" class="logo-title">河边预警系统</span>
        </transition>
      </div>

      <!-- Menu -->
      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="route.path"
          :collapse="isCollapse && !isMobile"
          router
          class="side-menu"
        >
          <el-menu-item
            v-for="item in menuItems"
            :key="item.path"
            :index="item.path"
            @click="onMenuClick"
          >
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- Right side -->
    <el-container class="right-container">
      <!-- Header -->
      <el-header class="header" height="56px">
        <div class="header-left">
          <div class="collapse-btn" @click="toggleSidebar">
            <el-icon :size="18">
              <Fold v-if="!isCollapse && !isMobile" />
              <Expand v-else-if="!isMobile" />
              <span v-else style="font-size:18px">&#9776;</span>
            </el-icon>
          </div>

          <el-breadcrumb separator="/" v-if="!isMobile" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
          <span v-else class="mobile-title">{{ route.meta.title }}</span>
        </div>

        <div class="header-right">
          <span class="header-time" v-if="!isMobile">{{ currentTime }}</span>

          <div class="header-action" @click="router.push('/alerts')">
            <el-badge :value="alertStore.unhandledCount" :hidden="alertStore.unhandledCount === 0" :max="99">
              <el-icon :size="18"><Bell /></el-icon>
            </el-badge>
          </div>

          <div class="header-action" @click="toggleFullscreen" v-if="!isMobile">
            <el-icon :size="18"><FullScreen /></el-icon>
          </div>

          <el-dropdown @command="handleCommand" trigger="click">
            <div class="user-wrapper">
              <div class="user-avatar">{{ avatarText }}</div>
              <span class="user-name" v-if="!isMobile">{{ userInfo.realName || userInfo.username }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <div style="padding: 4px 0;">
                    <div style="font-weight: 500; color: #303133;">{{ userInfo.realName || userInfo.username }}</div>
                    <div style="font-size: 12px; color: #909399; margin-top: 2px;">{{ userInfo.username }}</div>
                  </div>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Main content -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>

  <AlertPopup />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore, useAlertStore } from '../store'
import AlertPopup from '../components/AlertPopup.vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const alertStore = useAlertStore()

const isCollapse = ref(false)
const showDrawer = ref(false)
const windowWidth = ref(window.innerWidth)
const currentTime = ref('')

const isMobile = computed(() => windowWidth.value < 768)

const sidebarWidth = computed(() => {
  if (isMobile.value) return showDrawer.value ? '220px' : '0px'
  return isCollapse.value ? '64px' : '210px'
})

const avatarText = computed(() => {
  const name = userInfo.value.realName || userInfo.value.username || 'U'
  return name.charAt(0)
})

const toggleSidebar = () => {
  if (isMobile.value) showDrawer.value = !showDrawer.value
  else isCollapse.value = !isCollapse.value
}

const onMenuClick = () => {
  if (isMobile.value) showDrawer.value = false
}

const userInfo = computed(() => userStore.userInfo)

const allMenuItems = [
  { path: '/dashboard', title: '实时数据看板', icon: 'DataBoard' },
  { path: '/monitor', title: '实时监控', icon: 'VideoCamera' },
  { path: '/map', title: '地图总览', icon: 'Location' },
  { path: '/alerts', title: '预警记录管理', icon: 'Bell' },
  { path: '/data', title: '历史数据查询', icon: 'TrendCharts' },
  { path: '/attendance', title: '工作考勤', icon: 'Clock' },
  { path: '/devices', title: '设备管理', icon: 'Monitor', adminOnly: true },
  { path: '/users', title: '用户管理', icon: 'User', adminOnly: true }
]

const menuItems = computed(() => {
  if (userStore.isAdmin) return allMenuItems
  return allMenuItems.filter(item => !item.adminOnly)
})

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen()
}

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

const onResize = () => {
  windowWidth.value = window.innerWidth
  if (!isMobile.value) showDrawer.value = false
}

let stompClient = null
let timeTimer = null

onMounted(() => {
  window.addEventListener('resize', onResize)
  alertStore.refreshCount()
  updateTime()
  timeTimer = setInterval(updateTime, 1000)

  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      stompClient.subscribe('/topic/alert', () => alertStore.refreshCount())
    }
  })
  stompClient.activate()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  clearInterval(timeTimer)
  if (stompClient) stompClient.deactivate()
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

/* ===== Sidebar ===== */
.sidebar {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.6);
}

.sidebar-drawer {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  z-index: 2000;
}

.drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1999;
  backdrop-filter: blur(4px);
}

/* Logo */
.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar-logo.collapsed {
  justify-content: center;
  padding: 0;
}

.logo-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-title {
  color: #1a1a2e;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

/* Menu */
.menu-scrollbar {
  flex: 1;
  overflow: hidden;
}

.side-menu {
  border-right: none;
  background: transparent;
  padding: 6px 8px;
}

.side-menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  margin: 2px 0;
  border-radius: 8px;
  color: #606266;
  font-size: 13px;
  transition: all 0.2s;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(56, 97, 251, 0.06);
  color: #303133;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(56, 97, 251, 0.12), rgba(139, 92, 246, 0.06));
  color: #3861fb;
  font-weight: 600;
}

/* ===== Right container ===== */
.right-container {
  flex-direction: column;
  overflow: hidden;
}

/* Header */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
  padding: 0 20px;
  height: 56px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  cursor: pointer;
  color: #606266;
  display: flex;
  align-items: center;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: #f5f7fa;
  color: #3861fb;
}

.breadcrumb {
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-time {
  color: #909399;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  margin-right: 8px;
}

.header-action {
  cursor: pointer;
  color: #606266;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
}

.header-action:hover {
  background: #f5f7fa;
  color: #3861fb;
}

/* User */
.user-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  margin-left: 4px;
  transition: background 0.2s;
}

.user-wrapper:hover {
  background: #f5f7fa;
}

.user-avatar {
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #3861fb, #8b5cf6);
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-name {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.user-arrow {
  color: #909399;
  font-size: 12px;
}

.mobile-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* Main content */
.main-content {
  background: transparent;
  overflow-y: auto;
  padding: 16px;
}

/* Page transition */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.18s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Scrollbar */
.main-content::-webkit-scrollbar {
  width: 5px;
}

.main-content::-webkit-scrollbar-thumb {
  background: rgba(56, 97, 251, 0.15);
  border-radius: 3px;
}

.main-content::-webkit-scrollbar-track {
  background: transparent;
}
</style>
