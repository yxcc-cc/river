<template>
  <div class="monitor-page">
    <!-- Status bar -->
    <div class="status-bar">
      <div class="status-info">
        <el-tag :type="hasAnyAlert ? 'danger' : 'success'" size="large" effect="dark">
          <el-icon v-if="hasAnyAlert"><WarningFilled /></el-icon>
          <el-icon v-else><CircleCheckFilled /></el-icon>
          {{ hasAnyAlert ? '检测到入侵' : '监控正常' }}
        </el-tag>
        <span class="device-count">在线设备: {{ onlineCount }}/{{ devices.length }}</span>
      </div>
      <el-button-group>
        <el-button
          v-for="mode in ['grid', 'single']"
          :key="mode"
          :type="viewMode === mode ? 'primary' : ''"
          size="small"
          @click="viewMode = mode"
        >
          {{ mode === 'grid' ? '多画面' : '单画面' }}
        </el-button>
      </el-button-group>
    </div>

    <!-- 多画面模式 -->
    <div v-if="viewMode === 'grid'" class="grid-view">
      <el-row :gutter="12">
        <el-col
          v-for="dev in devices"
          :key="dev.id"
          :xs="24"
          :sm="12"
        >
          <el-card class="stream-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <div class="device-title">
                  <el-tag
                    :type="dev.status === 'ALERT' ? 'danger' : dev.status === 'SAFE' ? 'success' : 'info'"
                    size="small"
                    effect="dark"
                  >
                    {{ dev.status === 'ALERT' ? '报警' : dev.status === 'SAFE' ? '正常' : '离线' }}
                  </el-tag>
                  <span class="device-name">{{ dev.name }}</span>
                  <span class="device-id-tag">{{ dev.id }}</span>
                </div>
                <span class="stream-dot" :class="{ connected: dev.connected }">
                  ● {{ dev.connected ? '已连接' : '未连接' }}
                </span>
              </div>
            </template>
            <div class="stream-container" :class="{ 'alert-flash': dev.status === 'ALERT' }">
              <img
                :src="dev.streamUrl"
                class="stream-image"
                @load="dev.connected = true"
                @error="dev.connected = false"
                :alt="dev.name"
              />
              <div v-if="!dev.connected" class="stream-overlay">
                <el-icon :size="40" color="#c0c4cc"><VideoCamera /></el-icon>
                <p>视频流未连接</p>
              </div>
              <div v-if="dev.status === 'ALERT'" class="alert-badge">
                <el-icon><WarningFilled /></el-icon> 入侵报警
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 单画面模式（选中设备大屏） -->
    <div v-else>
      <el-row :gutter="16">
        <el-col :xs="24" :lg="16">
          <el-card class="stream-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <div class="device-title">
                  <span class="device-name">{{ selectedDevice.name }} - 实时画面</span>
                  <el-select v-model="selectedDevId" size="small" style="width: 180px; margin-left: 12px">
                    <el-option
                      v-for="dev in devices"
                      :key="dev.id"
                      :label="`${dev.name} (${dev.id})`"
                      :value="dev.id"
                    />
                  </el-select>
                </div>
                <span class="stream-dot" :class="{ connected: selectedDevice.connected }">
                  ● {{ selectedDevice.connected ? '已连接' : '未连接' }}
                </span>
              </div>
            </template>
            <div class="stream-container large" :class="{ 'alert-flash': selectedDevice.status === 'ALERT' }">
              <img
                :src="selectedDevice.streamUrl"
                class="stream-image"
                @load="selectedDevice.connected = true"
                @error="selectedDevice.connected = false"
                :alt="selectedDevice.name"
              />
              <div v-if="!selectedDevice.connected" class="stream-overlay">
                <el-icon :size="48" color="#c0c4cc"><VideoCamera /></el-icon>
                <p>视频流未连接</p>
                <p class="hint">请先运行: <code>python tools/vision_multi.py</code></p>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="8">
          <!-- 检测状态 -->
          <el-card class="status-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span><el-icon><DataBoard /></el-icon> 检测状态</span>
              </div>
            </template>
            <div class="status-grid">
              <div class="status-item">
                <span class="status-label">检测状态</span>
                <el-tag :type="selectedDevice.status === 'ALERT' ? 'danger' : 'success'" effect="dark">
                  {{ selectedDevice.status === 'ALERT' ? '入侵报警' : '安全' }}
                </el-tag>
              </div>
              <div class="status-item">
                <span class="status-label">视频流</span>
                <el-tag :type="selectedDevice.connected ? 'success' : 'info'" effect="plain">
                  {{ selectedDevice.connected ? '已连接' : '未连接' }}
                </el-tag>
              </div>
            </div>
          </el-card>

          <!-- 截图记录 -->
          <el-card class="history-card" shadow="hover" style="margin-top: 16px">
            <template #header>
              <div class="card-header">
                <span><el-icon><Picture /></el-icon> 入侵截图记录</span>
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-tag type="info" size="small">最近 {{ captureList.length }} 条</el-tag>
                  <el-button type="danger" size="small" plain @click="handleClearCaptures">清空</el-button>
                </div>
              </div>
            </template>
            <div class="capture-list" v-if="captureList.length > 0">
              <div v-for="(item, index) in captureList" :key="index" class="capture-item">
                <img :src="item.url" class="capture-thumb" @click="previewImage(item.url)" />
                <div class="capture-info" @click="previewImage(item.url)">
                  <span class="capture-time">{{ formatTime(item.filename) }}</span>
                  <span class="capture-date">{{ item.deviceId }}</span>
                </div>
                <el-button type="danger" :icon="Delete" circle size="small" @click.stop="handleDeleteCapture(item, index)" />
              </div>
            </div>
            <el-empty v-else description="暂无入侵记录" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- Image preview -->
    <el-dialog v-model="previewVisible" title="截图预览" width="80%" destroy-on-close>
      <img :src="previewUrl" style="width: 100%" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { WarningFilled, CircleCheckFilled, VideoCamera, Picture, DataBoard, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCaptureList, deleteCapture, clearCaptures } from '../api'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const route = useRoute()

// 设备列表（和 vision_multi.py 的 DEVICES 对应）
const devices = ref([
  { id: 'DEV-001', name: '甘棠湖公园(ESP32-CAM)', streamUrl: '/stream?dev=DEV-001', connected: false, status: 'SAFE' },
  { id: 'DEV-006', name: '烟水亭湖畔(USB摄像头)', streamUrl: '/stream?dev=DEV-006', connected: false, status: 'SAFE' },
])

const viewMode = ref('grid')  // grid = 多画面, single = 单画面
const selectedDevId = ref('DEV-001')
const captureList = ref([])
const previewVisible = ref(false)
const previewUrl = ref('')

let stompClient = null
let checkTimer = null
let alertTimers = {}

const selectedDevice = computed(() =>
  devices.value.find(d => d.id === selectedDevId.value) || devices.value[0]
)

const hasAnyAlert = computed(() =>
  devices.value.some(d => d.status === 'ALERT')
)

const onlineCount = computed(() =>
  devices.value.filter(d => d.connected).length
)

const loadCaptureList = async () => {
  try {
    // 加载所有设备的截图
    const all = []
    for (const dev of devices.value) {
      const res = await getCaptureList(dev.id, 10)
      if (res.code === 200 && res.data) {
        all.push(...res.data.map(item => ({ ...item, deviceId: dev.id })))
      }
    }
    // 按时间倒序
    all.sort((a, b) => (b.filename || '').localeCompare(a.filename || ''))
    captureList.value = all.slice(0, 20)
  } catch (e) {
    console.error('Failed to load capture list:', e)
  }
}

const formatTime = (filename) => {
  if (!filename) return ''
  const parts = filename.replace('.jpg', '').split('_')
  if (parts.length >= 2) {
    const timeStr = parts[parts.length - 1]
    return `${timeStr.substring(0, 2)}:${timeStr.substring(2, 4)}:${timeStr.substring(4, 6)}`
  }
  return filename
}

const previewImage = (url) => {
  previewUrl.value = url
  previewVisible.value = true
}

const handleDeleteCapture = async (item, index) => {
  try {
    await ElMessageBox.confirm('确定删除这张截图？', '提示', { type: 'warning' })
    await deleteCapture(item.url)
    captureList.value.splice(index, 1)
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleClearCaptures = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有入侵截图？', '警告', { type: 'warning' })
    for (const dev of devices.value) {
      await clearCaptures(dev.id)
    }
    captureList.value = []
    ElMessage.success('清空成功')
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// 轮询多路流状态
const checkStreamStatus = () => {
  checkTimer = setInterval(async () => {
    try {
      const resp = await fetch('http://localhost:8083/status')
      if (resp.ok) {
        const data = await resp.json()
        for (const dev of devices.value) {
          if (data[dev.id]) {
            dev.connected = true
            dev.status = data[dev.id].status || 'SAFE'
          }
        }
      }
    } catch {
      // 多路流服务未启动时，通过 img load/error 判断
    }
  }, 5000)
}

// WebSocket 监听所有设备的预警
const setupWebSocket = () => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      stompClient.subscribe('/topic/alert', (message) => {
        try {
          const alert = JSON.parse(message.body)
          const dev = devices.value.find(d => d.id === alert.deviceId)
          if (dev && alert.alertType === 'INTRUSION_ALARM') {
            dev.status = 'ALERT'
            clearTimeout(alertTimers[dev.id])
            alertTimers[dev.id] = setTimeout(() => { dev.status = 'SAFE' }, 30000)
            setTimeout(() => { loadCaptureList() }, 2000)
          }
        } catch (e) {
          console.error('Failed to parse alert:', e)
        }
      })
    }
  })
  stompClient.activate()
}

onMounted(() => {
  // 支持从地图跳转：/monitor?dev=DEV-001
  const devParam = route.query.dev
  if (devParam && devices.value.find(d => d.id === devParam)) {
    selectedDevId.value = devParam
    viewMode.value = 'single'
  }
  loadCaptureList()
  checkStreamStatus()
  setupWebSocket()
})

onUnmounted(() => {
  clearInterval(checkTimer)
  Object.values(alertTimers).forEach(clearTimeout)
  if (stompClient) stompClient.deactivate()
})
</script>

<style scoped>
.monitor-page {
  padding: 0;
}

.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  padding: 12px 20px;
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.status-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.device-count {
  color: #606266;
  font-size: 14px;
}

.stream-card {
  margin-bottom: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.device-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-name {
  font-weight: 600;
  font-size: 14px;
}

.device-id-tag {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}

.stream-dot {
  font-size: 13px;
  color: #f56c6c;
}

.stream-dot.connected {
  color: #67c23a;
}

.stream-container {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stream-container.large {
  min-height: 480px;
}

.stream-image {
  width: 100%;
  display: block;
}

.stream-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  gap: 12px;
}

.stream-overlay p {
  margin: 0;
  font-size: 16px;
}

.stream-overlay .hint {
  font-size: 13px;
  color: #c0c4cc;
}

code {
  background: #333;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
  color: #409eff;
}

.alert-flash {
  animation: alert-border 1s infinite;
}

@keyframes alert-border {
  0%, 100% { box-shadow: 0 0 0 3px transparent; }
  50% { box-shadow: 0 0 0 3px #f56c6c; }
}

.alert-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: #f56c6c;
  color: #fff;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.status-card .status-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-label {
  color: #606266;
  font-size: 14px;
}

.capture-list {
  max-height: 400px;
  overflow-y: auto;
}

.capture-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}

.capture-item:hover {
  background: rgba(56, 97, 251, 0.04);
}

.capture-item:last-child {
  border-bottom: none;
}

.capture-thumb {
  width: 80px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
}

.capture-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.capture-time {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.capture-date {
  font-size: 12px;
  color: #909399;
}
</style>
