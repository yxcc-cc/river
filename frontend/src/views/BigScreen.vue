<template>
  <div class="big-screen">
    <!-- Header -->
    <div class="screen-header">
      <div class="header-left">
        <span class="system-title">河边安全智能预警系统</span>
        <span class="system-subtitle">River Safety Early Warning System</span>
      </div>
      <div class="header-center">
        <span class="current-time">{{ currentTime }}</span>
      </div>
      <div class="header-right">
        <el-button type="primary" text size="small" @click="exitFullscreen" class="exit-btn">
          <el-icon><Close /></el-icon> 退出全屏
        </el-button>
      </div>
    </div>

    <!-- Main Content -->
    <div class="screen-body">
      <!-- Left Column -->
      <div class="screen-left">
        <!-- Stats Cards -->
        <div class="stats-panel">
          <div class="panel-title">系统概览</div>
          <div class="stats-grid">
            <div class="stat-box" v-for="(s, i) in statCards" :key="i" :style="{ animationDelay: i * 0.1 + 's' }">
              <div class="stat-num" :style="{ color: s.color }">{{ animatedNums[i] ?? 0 }}</div>
              <div class="stat-txt">{{ s.label }}</div>
            </div>
          </div>
        </div>

        <!-- Alert Type Chart -->
        <div class="chart-panel">
          <div class="panel-title">预警类型分布</div>
          <div ref="alertTypeRef" class="chart-area"></div>
        </div>
      </div>

      <!-- Center Column -->
      <div class="screen-center">
        <!-- Device Status -->
        <div class="device-panel">
          <div class="panel-title">设备实时状态</div>
          <div class="device-list">
            <div v-for="device in devices" :key="device.id" class="device-item" :class="getDeviceStatusClass(device)">
              <div class="device-name">{{ device.location }}</div>
              <div class="device-data-row">
                <div class="device-metric">
                  <span class="metric-label">水位</span>
                  <span class="metric-value" :style="{ color: getLevelColor(device) }">
                    {{ getWaterLevel(device) }} cm
                  </span>
                  <span class="metric-tag" :style="{ background: getLevelColor(device) }">
                    {{ getLevelLabel(device) }}
                  </span>
                </div>
                <div class="device-metric">
                  <span class="metric-label">入侵</span>
                  <span class="metric-value" :style="{ color: isIntrusion(device) ? '#f56c6c' : '#67c23a' }">
                    {{ isIntrusion(device) ? '检测到' : '正常' }}
                  </span>
                </div>
                <div class="device-metric">
                  <span class="metric-label">状态</span>
                  <span class="metric-value" :style="{ color: device.status === 1 ? '#67c23a' : '#909399' }">
                    {{ device.status === 1 ? '在线' : '离线' }}
                  </span>
                </div>
              </div>
              <!-- Water level bar -->
              <div class="water-bar-container">
                <div class="water-bar" :style="{ width: getWaterPercent(device), background: getLevelColor(device) }"></div>
                <div class="water-threshold-line" :style="{ left: '100%' }"></div>
              </div>
            </div>
            <div v-if="devices.length === 0" class="no-device">暂无设备数据</div>
          </div>
        </div>

        <!-- Alert Trend -->
        <div class="chart-panel">
          <div class="panel-title">今日预警趋势</div>
          <div ref="alertTrendRef" class="chart-area"></div>
        </div>
      </div>

      <!-- Right Column -->
      <div class="screen-right">
        <!-- Water Trend -->
        <div class="chart-panel">
          <div class="panel-title">水位趋势 (24h)</div>
          <div ref="waterTrendRef" class="chart-area"></div>
        </div>

        <!-- Recent Alerts -->
        <div class="alert-panel">
          <div class="panel-title">最近预警</div>
          <div class="alert-list">
            <div v-for="alert in recentAlerts" :key="alert.id" class="alert-item">
              <span class="alert-dot" :class="alert.alertType === 'WATER_ALARM' ? 'dot-water' : 'dot-intrusion'"></span>
              <span class="alert-text">
                {{ alert.alertType === 'WATER_ALARM' ? '水位' : '入侵' }}
                {{ alert.deviceLocation || alert.deviceId }}
              </span>
              <span class="alert-time">{{ alert.alertTime?.substring(11, 19) || '' }}</span>
            </div>
            <div v-if="recentAlerts.length === 0" class="no-alert">暂无预警</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getDevices, getRealtimeData, getOverview, getAlerts, getWaterTrend } from '../api'
import { getAlertLevel } from '../utils/alertLevel'

const router = useRouter()
const overview = ref({})
const devices = ref([])
const realtimeData = ref({})
const recentAlerts = ref([])
const currentTime = ref('')
const animatedNums = ref([0, 0, 0, 0])

const statCards = [
  { key: 'totalDevices', label: '设备总数', color: '#409eff' },
  { key: 'onlineDevices', label: '在线设备', color: '#67c23a' },
  { key: 'todayAlerts', label: '今日预警', color: '#e6a23c' },
  { key: 'unhandledAlerts', label: '待处理', color: '#f56c6c' }
]

// 数字跳动动画
const animateNums = () => {
  statCards.forEach((s, i) => {
    const target = overview.value[s.key] || 0
    const start = animatedNums.value[i] || 0
    const diff = target - start
    if (diff === 0) return
    const duration = 800
    const startTime = performance.now()
    const step = (now) => {
      const progress = Math.min((now - startTime) / duration, 1)
      const ease = 1 - Math.pow(1 - progress, 3) // easeOutCubic
      animatedNums.value[i] = Math.round(start + diff * ease)
      if (progress < 1) requestAnimationFrame(step)
    }
    requestAnimationFrame(step)
  })
}

const alertTypeRef = ref()
const alertTrendRef = ref()
const waterTrendRef = ref()
let alertTypeChart, alertTrendChart, waterTrendChart
let refreshTimer, timeTimer

// Time display
const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

// Helper functions
const getWaterLevel = (device) => {
  return realtimeData.value[device.id]?.waterData?.waterLevel ?? '--'
}

const isIntrusion = (device) => {
  return realtimeData.value[device.id]?.intrusion?.humanDetected === 1
}

const getLevelColor = (device) => {
  const level = getAlertLevel(
    realtimeData.value[device.id]?.waterData?.waterLevel,
    device.waterThreshold
  )
  return level.color
}

const getLevelLabel = (device) => {
  const level = getAlertLevel(
    realtimeData.value[device.id]?.waterData?.waterLevel,
    device.waterThreshold
  )
  return level.label
}

const getWaterPercent = (device) => {
  const wl = realtimeData.value[device.id]?.waterData?.waterLevel
  const threshold = device.waterThreshold
  if (!wl || !threshold) return '0%'
  return Math.min(100, (wl / threshold) * 100) + '%'
}

const getDeviceStatusClass = (device) => {
  if (device.status !== 1) return 'offline'
  const level = getAlertLevel(
    realtimeData.value[device.id]?.waterData?.waterLevel,
    device.waterThreshold
  )
  return level.key
}

// Load data
const loadData = async () => {
  try {
    const [ov, dev, al] = await Promise.all([
      getOverview(),
      getDevices(),
      getAlerts({ page: 1, size: 8 })
    ])
    overview.value = ov.data || {}
    devices.value = dev.data || []
    recentAlerts.value = al.data?.records || []
    animateNums()

    for (const d of devices.value) {
      try {
        const r = await getRealtimeData(d.id)
        realtimeData.value[d.id] = r.data || {}
      } catch (e) {}
    }

    renderCharts()
  } catch (e) {}
}

const renderCharts = async () => {
  await nextTick()

  // Alert Type Pie
  if (alertTypeRef.value) {
    try { alertTypeChart?.dispose() } catch (e) {}
    alertTypeChart = echarts.init(alertTypeRef.value, 'dark')
    alertTypeChart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item' },
      color: ['#409eff', '#e6a23c'],
      series: [{
        type: 'pie',
        radius: ['35%', '60%'],
        center: ['50%', '55%'],
        label: { color: '#ccc', fontSize: 11, formatter: '{b}\n{c}次' },
        data: [
          { value: overview.value.waterAlarms || 0, name: '水位预警' },
          { value: overview.value.intrusionAlarms || 0, name: '入侵预警' }
        ]
      }]
    })
  }

  // Alert Trend Bar
  if (alertTrendRef.value) {
    try { alertTrendChart?.dispose() } catch (e) {}
    alertTrendChart = echarts.init(alertTrendRef.value, 'dark')
    const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)
    const waterByHour = new Array(24).fill(0)
    const intrusionByHour = new Array(24).fill(0)
    const today = new Date().toISOString().substring(0, 10)
    recentAlerts.value.forEach(a => {
      if (a.alertTime && a.alertTime.startsWith(today)) {
        const h = parseInt(a.alertTime.substring(11, 13))
        if (a.alertType === 'WATER_ALARM') waterByHour[h]++
        else intrusionByHour[h]++
      }
    })
    alertTrendChart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      legend: { data: ['水位', '入侵'], textStyle: { color: '#ccc', fontSize: 10 }, bottom: 0 },
      color: ['#409eff', '#e6a23c'],
      grid: { left: 35, right: 10, bottom: 30, top: 10 },
      xAxis: { type: 'category', data: hours, axisLabel: { color: '#888', fontSize: 9, interval: 3 } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#888', fontSize: 9 }, splitLine: { lineStyle: { color: '#333' } } },
      series: [
        { name: '水位', type: 'bar', stack: 'total', data: waterByHour },
        { name: '入侵', type: 'bar', stack: 'total', data: intrusionByHour }
      ]
    })
  }

  // Water Trend Line
  if (waterTrendRef.value && devices.value.length > 0) {
    try { waterTrendChart?.dispose() } catch (e) {}
    waterTrendChart = echarts.init(waterTrendRef.value, 'dark')
    try {
      const res = await getWaterTrend(devices.value[0].id, 24)
      const data = res.data || []
      const threshold = devices.value[0].waterThreshold || 50
      waterTrendChart.setOption({
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: data.map(d => d.collectTime?.substring(11, 16) || ''), axisLabel: { color: '#888', fontSize: 9 } },
        yAxis: { type: 'value', name: 'cm', nameTextStyle: { color: '#888' }, axisLabel: { color: '#888', fontSize: 9 }, splitLine: { lineStyle: { color: '#333' } } },
        series: [{
          type: 'line',
          data: data.map(d => d.waterLevel),
          smooth: true,
          areaStyle: { opacity: 0.2, color: '#409eff' },
          lineStyle: { width: 2, color: '#409eff' },
          markLine: {
            silent: true,
            data: [
              { yAxis: threshold, lineStyle: { color: '#f56c6c', type: 'dashed' }, label: { formatter: '危险', color: '#f56c6c' } },
              { yAxis: threshold * 0.85, lineStyle: { color: '#e6a23c', type: 'dotted' }, label: { formatter: '警告', color: '#e6a23c' } }
            ]
          }
        }],
        grid: { left: 40, right: 15, bottom: 25, top: 25 }
      })
    } catch (e) {}
  }
}

const exitFullscreen = () => {
  if (document.exitFullscreen) document.exitFullscreen()
  router.push('/dashboard')
}

onMounted(() => {
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
  loadData()
  refreshTimer = setInterval(loadData, 15000)

  // Auto fullscreen
  document.documentElement.requestFullscreen?.().catch(() => {})

  window.addEventListener('resize', () => {
    try { alertTypeChart?.resize() } catch (e) {}
    try { alertTrendChart?.resize() } catch (e) {}
    try { waterTrendChart?.resize() } catch (e) {}
  })
})

onUnmounted(() => {
  clearInterval(refreshTimer)
  clearInterval(timeTimer)
  alertTypeChart?.dispose()
  alertTrendChart?.dispose()
  waterTrendChart?.dispose()
})
</script>

<style scoped>
.big-screen {
  width: 100vw;
  height: 100vh;
  background: #0a0e27;
  color: #fff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Header */
.screen-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 24px;
  background: linear-gradient(90deg, #0d1b3e, #162d5a, #0d1b3e);
  border-bottom: 2px solid #1e90ff;
  height: 50px;
}
.header-left { display: flex; align-items: baseline; gap: 12px; }
.system-title { font-size: 22px; font-weight: bold; color: #00d4ff; letter-spacing: 4px; }
.system-subtitle { font-size: 11px; color: #5a8cb5; }
.current-time { font-size: 16px; color: #00d4ff; font-family: monospace; }
.exit-btn { color: #888 !important; }

/* Body */
.screen-body {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px;
  overflow: hidden;
}
.screen-left { flex: 0 0 22%; display: flex; flex-direction: column; gap: 12px; }
.screen-center { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.screen-right { flex: 0 0 28%; display: flex; flex-direction: column; gap: 12px; }

/* Panels */
.stats-panel, .chart-panel, .device-panel, .alert-panel {
  background: rgba(13, 27, 62, 0.8);
  border: 1px solid #1e3a5f;
  border-radius: 6px;
  padding: 10px;
  flex: 1;
}

.panel-title {
  font-size: 14px;
  color: #00d4ff;
  font-weight: bold;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #1e3a5f;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.stat-box {
  text-align: center;
  padding: 10px 0;
  background: rgba(30, 58, 95, 0.4);
  border-radius: 6px;
}
.stat-num { font-size: 32px; font-weight: bold; }
.stat-txt { font-size: 12px; color: #888; margin-top: 4px; }

/* Charts */
.chart-area { height: 200px; }

/* Device List */
.device-list { overflow-y: auto; max-height: calc(100% - 40px); }
.device-item {
  padding: 10px;
  margin-bottom: 8px;
  background: rgba(30, 58, 95, 0.4);
  border-radius: 6px;
  border-left: 3px solid #67c23a;
}
.device-item.warning { border-left-color: #e6a23c; }
.device-item.danger { border-left-color: #f56c6c; }
.device-item.notice { border-left-color: #409eff; }
.device-item.offline { border-left-color: #909399; opacity: 0.5; }
.device-name { font-size: 13px; color: #ccc; margin-bottom: 6px; }
.device-data-row { display: flex; gap: 16px; }
.device-metric { display: flex; align-items: center; gap: 6px; }
.metric-label { font-size: 11px; color: #888; }
.metric-value { font-size: 14px; font-weight: bold; }
.metric-tag { font-size: 10px; color: #fff; padding: 1px 6px; border-radius: 10px; }
.water-bar-container {
  margin-top: 6px;
  height: 4px;
  background: #1e3a5f;
  border-radius: 2px;
  position: relative;
}
.water-bar { height: 100%; border-radius: 2px; transition: width 0.5s; }
.no-device, .no-alert { text-align: center; color: #555; padding: 20px; }

/* Alert List */
.alert-list { overflow-y: auto; max-height: calc(100% - 40px); }
.alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #1e3a5f;
  font-size: 12px;
}
.alert-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-water { background: #409eff; }
.dot-intrusion { background: #e6a23c; }
.alert-text { flex: 1; color: #ccc; }
.alert-time { color: #666; font-family: monospace; }
</style>
