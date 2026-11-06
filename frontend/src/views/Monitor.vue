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
          v-for="mode in ['single', 'grid']"
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
                v-if="dev.streamUrl"
                :src="dev.streamUrl"
                class="stream-image"
                @load="dev.connected = true"
                @error="dev.connected = false"
                :alt="dev.name"
              />
              <div v-if="!dev.streamUrl" class="stream-overlay">
                <el-icon :size="40" color="#9a9a9a"><VideoCamera /></el-icon>
                <p>该点位未配置视频</p>
                <p class="hint">在设备管理里填上视频流地址</p>
              </div>
              <div v-else-if="!dev.connected" class="stream-overlay">
                <el-icon :size="40" color="#9a9a9a"><VideoCamera /></el-icon>
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
                v-if="selectedDevice.streamUrl"
                :src="selectedDevice.streamUrl"
                class="stream-image"
                @load="selectedDevice.connected = true"
                @error="selectedDevice.connected = false"
                :alt="selectedDevice.name"
              />
              <div v-if="!selectedDevice.streamUrl" class="stream-overlay">
                <el-icon :size="48" color="#9a9a9a"><VideoCamera /></el-icon>
                <p>该点位未配置视频</p>
                <p class="hint">在设备管理里填上这个点位的视频流地址</p>
              </div>
              <div v-else-if="!selectedDevice.connected" class="stream-overlay">
                <el-icon :size="48" color="#9a9a9a"><VideoCamera /></el-icon>
                <p>视频流未连接</p>
                <p class="hint">检查摄像头电源、WiFi，以及本机的检测脚本是否在跑</p>
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
                  <span class="capture-date">{{ formatDate(item.date) }} · {{ item.deviceId }}</span>
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
import { getDevices, getCaptureList, deleteCapture, clearCaptures } from '../api'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const route = useRoute()

// 全局视频流地址（隧道），后端 /api/config/stream 下发，隧道换了只改后端配置
const _q = new URLSearchParams(window.location.search)
const STREAM_OVERRIDE = _q.get('stream') || ''

let _globalStream = ''
async function resolveGlobalStream() {
  if (STREAM_OVERRIDE) return STREAM_OVERRIDE
  if (_globalStream) return _globalStream
  try {
    const r = await fetch('/api/config/stream')
    const j = await r.json()
    const url = j?.data?.url
    if (url) {
      _globalStream = url
      return url
    }
  } catch (e) {
    // 后端没配就回退同源代理
  }
  return '/stream'
}

// 设备列表：从后端 /api/devices 拉，支持多点位
// 每台设备的流地址优先用自己配的 streamUrl，没配就用全局地址
const devices = ref([])
const viewMode = ref('single')  // single = 单画面（默认）, grid = 多画面
const selectedDevId = ref('')
const captureList = ref([])
const previewVisible = ref(false)
const previewUrl = ref('')

const loadingDevices = ref(true)
const deviceLoadError = ref('')

async function loadDevices() {
  loadingDevices.value = true
  deviceLoadError.value = ''
  try {
    const globalStream = await resolveGlobalStream()
    const res = await getDevices()
    const list = (res && res.data) || []
    // 只有一台设备时才用全局隧道地址兜底；
    // 多点位时没配流的设备就留空（显示"未配置视频"），否则多路会抢同一路流
    const fallbackStream = list.length === 1 ? globalStream : ''
    devices.value = list.map(d => ({
      id: d.id,
      name: d.location || d.id,
      // 自己配了流就用自己的，否则按上面的规则兜底
      streamUrl: d.streamUrl || fallbackStream,
      rawUrl: d.streamUrl || fallbackStream,
      aiUrl: d.streamUrl || fallbackStream,
      connected: false,
      status: 'SAFE',
      waterThreshold: d.waterThreshold,
      online: d.status === 1,
      lastHeartbeat: d.lastHeartbeat
    }))
    if (devices.value.length > 0 && !devices.value.some(d => d.id === selectedDevId.value)) {
      selectedDevId.value = devices.value[0].id
    }
  } catch (e) {
    console.error('加载设备列表失败:', e)
    deviceLoadError.value = '设备列表加载失败，请检查后端连接'
  } finally {
    loadingDevices.value = false
  }
}

const selectedDevice = computed(() =>
  devices.value.find(d => d.id === selectedDevId.value) || devices.value[0] || {}
)

const hasAnyAlert = computed(() =>
  devices.value.some(d => d.status === 'ALERT')
)

const onlineCount = computed(() =>
  devices.value.filter(d => d.online || d.connected).length
)

let stompClient = null
let checkTimer = null
let alertTimers = {}

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

// 文件名格式：{设备ID}_{HHmmss}_{毫秒}.jpg
// 比如 2462ABEFC95C_165921_406.jpg  →  16:59:21
// 注意不能取 split('_') 的最后一段，那是毫秒，会显示成 "40:6:" 这种怪东西
const formatTime = (filename) => {
  if (!filename) return ''
  const m = filename.match(/_(\d{6})_/)
  if (m) {
    const t = m[1]
    return `${t.slice(0, 2)}:${t.slice(2, 4)}:${t.slice(4, 6)}`
  }
  return filename
}

// 20261027 → 10-27
const formatDate = (date) => {
  if (!date || date.length !== 8) return ''
  return `${date.slice(4, 6)}-${date.slice(6, 8)}`
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

onMounted(async () => {
  // 先把设备列表拉回来，后面所有逻辑都依赖它
  await loadDevices()

  // 支持从地图跳转：/monitor?dev=xxx
  const devParam = route.query.dev
  if (devParam && devices.value.some(d => d.id === devParam)) {
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
  color: #212121;
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
  color: #707070;
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
  color: #9a9a9a;
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
  color: #212121;
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
  color: #171717;
}

.capture-date {
  font-size: 12px;
  color: #707070;
}
</style>
