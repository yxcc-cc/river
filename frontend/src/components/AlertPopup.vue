<template>
  <!-- 中央弹窗遮罩 -->
  <div v-if="isCenter && toasts.length > 0" class="alert-overlay" @click="toasts = []"></div>

  <div class="toast-container" :class="{ 'toast-center': isCenter }">
    <transition-group name="toast-slide">
      <div
        v-for="alert in toasts"
        :key="alert.id"
        class="toast-item"
        :class="[
          alert.alertType === 'WATER_ALARM' ? 'toast-water' : 'toast-intrusion',
          { 'toast-center-item': isCenter }
        ]"
      >
        <div class="toast-accent" :class="{ 'accent-center': isCenter }"></div>
        <div class="toast-body">
          <div class="toast-header" :class="{ 'header-center': isCenter }">
            <span class="toast-icon" :class="{ 'icon-center': isCenter }">
              {{ alert.alertType === 'WATER_ALARM' ? '🌊' : '🚨' }}
            </span>
            <div class="toast-title-wrap">
              <span class="toast-title" :class="{ 'title-center': isCenter }">
                {{ alert.alertType === 'WATER_ALARM' ? '水位超限预警' : '人员入侵预警' }}
              </span>
              <span class="toast-time" :class="{ 'time-center': isCenter }">{{ formatTime(alert.alertTime) }}</span>
            </div>
          </div>
          <div class="toast-info" :class="{ 'info-center': isCenter }">
            <span class="toast-location">{{ alert.deviceLocation || alert.deviceId }}</span>
            <span v-if="alert.waterLevel" class="toast-detail" :class="{ 'detail-center': isCenter }">
              {{ alert.waterLevel }} cm
            </span>
          </div>
          <div class="toast-actions" :class="{ 'actions-center': isCenter }">
            <el-button :size="isCenter ? 'default' : 'small'" class="toast-btn" @click="dismiss(alert.id)">忽略</el-button>
            <el-button :size="isCenter ? 'default' : 'small'" class="toast-btn primary" @click="goToAlerts">
              <el-icon v-if="isCenter" style="margin-right:4px"><Warning /></el-icon>立即查看
            </el-button>
          </div>
          <!-- 自动消失进度条 -->
          <div class="toast-progress">
            <div class="toast-progress-bar" :class="alert.alertType === 'WATER_ALARM' ? 'bar-water' : 'bar-intrusion'"></div>
          </div>
        </div>
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAlertStore } from '../store'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { Warning } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const alertStore = useAlertStore()
const toasts = ref([])
let stompClient = null
let idCounter = 0

// 监控页面：右下角；其他页面：屏幕中央
const isCenter = computed(() => route.path !== '/monitor')

const AUTO_DISMISS = 8000 // 8秒自动消失

const formatTime = (t) => {
  if (!t) return ''
  return t.substring(11, 19)
}

const showToast = (alert) => {
  const id = ++idCounter
  const toast = { ...alert, id }
  toasts.value.push(toast)
  alertStore.refreshCount()

  // 播放提示音
  try {
    const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgipqZkIBxbXqMo6WQcFdPaIuoppF1YFVsjKCmk3VfVG2KoKWTdWBUbYqgpZN1YFRtiqClk3VgVG2KoKWTdQ==')
    audio.volume = 0.3
    audio.play().catch(() => {})
  } catch (e) {}

  // 自动消失
  setTimeout(() => dismiss(id), AUTO_DISMISS)

  // 最多显示4条
  if (toasts.value.length > 4) {
    toasts.value.shift()
  }
}

const dismiss = (id) => {
  const idx = toasts.value.findIndex(t => t.id === id)
  if (idx !== -1) toasts.value.splice(idx, 1)
}

const goToAlerts = () => {
  toasts.value = []
  router.push('/alerts')
}

onMounted(() => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      stompClient.subscribe('/topic/alert', (message) => {
        try {
          const alert = JSON.parse(message.body)
          showToast(alert)
        } catch (e) {}
      })
    }
  })
  stompClient.activate()
})

onUnmounted(() => {
  if (stompClient) stompClient.deactivate()
})
</script>

<style scoped>
/* ===== 遮罩层 ===== */
.alert-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(6px);
  z-index: 9998;
  animation: overlayIn 0.3s ease;
}
@keyframes overlayIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* ===== 容器 ===== */
.toast-container {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 9999;
  display: flex;
  flex-direction: column-reverse;
  gap: 10px;
  pointer-events: none;
  max-width: 360px;
}

/* 非监控页面：屏幕中央 */
.toast-container.toast-center {
  top: 50%;
  left: 50%;
  right: auto;
  bottom: auto;
  transform: translate(-50%, -50%);
  flex-direction: column;
  align-items: center;
  max-width: 480px;
}

/* ===== 右下角小Toast ===== */
.toast-item {
  display: flex;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12), 0 1px 4px rgba(0, 0, 0, 0.06);
  pointer-events: auto;
  min-width: 320px;
}

.toast-accent {
  width: 4px;
  flex-shrink: 0;
}
.toast-water .toast-accent { background: linear-gradient(180deg, #3861fb, #5b7cfa); }
.toast-intrusion .toast-accent { background: linear-gradient(180deg, #f56c6c, #e6a23c); }

.toast-body {
  flex: 1;
  padding: 12px 14px 8px;
  position: relative;
}

.toast-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.toast-icon { font-size: 16px; }
.toast-title { font-size: 14px; font-weight: 600; color: #1a1a2e; flex: 1; }
.toast-time { font-size: 12px; color: #909399; font-family: monospace; }

.toast-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.toast-location { font-size: 13px; color: #606266; }
.toast-detail { font-size: 13px; font-weight: 600; color: #f56c6c; }

.toast-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
.toast-btn { font-size: 12px; color: #909399 !important; padding: 4px 8px !important; }
.toast-btn.primary { color: #3861fb !important; font-weight: 500; }

/* 进度条 */
.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: rgba(0,0,0,0.04);
}
.toast-progress-bar {
  height: 100%;
  width: 100%;
  transform-origin: left;
  animation: progressShrink 8s linear forwards;
}
.bar-water { background: linear-gradient(90deg, #3861fb, #5b7cfa); }
.bar-intrusion { background: linear-gradient(90deg, #f56c6c, #e6a23c); }

@keyframes progressShrink {
  0%   { transform: scaleX(1); }
  100% { transform: scaleX(0); }
}

/* 右下角动画 */
.toast-slide-enter-active {
  animation: toastIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.toast-slide-leave-active {
  animation: toastOut 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.toast-slide-move {
  transition: transform 0.3s ease;
}
@keyframes toastIn {
  0%   { opacity: 0; transform: translateX(80px) scale(0.9); }
  100% { opacity: 1; transform: translateX(0) scale(1); }
}
@keyframes toastOut {
  0%   { opacity: 1; transform: translateX(0) scale(1); }
  100% { opacity: 0; transform: translateX(80px) scale(0.9); }
}

/* ===== 中央大号警告卡片 ===== */
.toast-center-item {
  min-width: 420px;
  border-radius: 16px;
  border: 2px solid transparent;
  animation: warningPulse 2s ease-in-out infinite;
}
.toast-center-item.toast-water {
  border-color: rgba(56, 97, 251, 0.3);
  box-shadow: 0 0 40px rgba(56, 97, 251, 0.2), 0 8px 32px rgba(0, 0, 0, 0.15);
}
.toast-center-item.toast-intrusion {
  border-color: rgba(245, 108, 108, 0.4);
  box-shadow: 0 0 40px rgba(245, 108, 108, 0.25), 0 8px 32px rgba(0, 0, 0, 0.15);
}

@keyframes warningPulse {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.02); }
}

.accent-center {
  width: 6px !important;
}

.toast-center-item .toast-body {
  padding: 20px 24px 16px;
}

.header-center {
  gap: 12px;
  margin-bottom: 12px;
}
.icon-center { font-size: 36px !important; }
.title-wrap-center { display: flex; flex-direction: column; }
.title-center { font-size: 20px !important; font-weight: 700 !important; }
.time-center { font-size: 14px !important; margin-top: 2px; }

.info-center {
  gap: 12px;
  margin-bottom: 16px;
  font-size: 15px;
}
.toast-center-item .toast-location { font-size: 15px; }
.detail-center { font-size: 18px !important; font-weight: 700 !important; }

.actions-center {
  gap: 12px;
  justify-content: center;
}
.actions-center .toast-btn {
  font-size: 14px !important;
  padding: 8px 20px !important;
  border-radius: 8px;
}
.actions-center .toast-btn.primary {
  background: linear-gradient(135deg, #f56c6c, #e6a23c) !important;
  color: #fff !important;
  border: none !important;
  font-weight: 600;
}

.toast-center-item .toast-progress {
  height: 4px;
}

/* 中央弹出动画 */
.toast-center .toast-slide-enter-active {
  animation: toastCenterIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.toast-center .toast-slide-leave-active {
  animation: toastCenterOut 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes toastCenterIn {
  0%   { opacity: 0; transform: scale(0.6); }
  100% { opacity: 1; transform: scale(1); }
}
@keyframes toastCenterOut {
  0%   { opacity: 1; transform: scale(1); }
  100% { opacity: 0; transform: scale(0.6); }
}
</style>
