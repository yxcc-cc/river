<template>
  <div class="dashboard">
    <!-- Statistics Cards -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ overview.totalDevices || 0 }}</div>
          <div class="stat-label">设备总数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card online">
          <div class="stat-value">{{ overview.onlineDevices || 0 }}</div>
          <div class="stat-label">在线设备</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card info">
          <div class="stat-value">{{ overview.todayAlerts || 0 }}</div>
          <div class="stat-label">今日预警</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card warning">
          <div class="stat-value">{{ overview.unhandledAlerts || 0 }}</div>
          <div class="stat-label">未处理预警</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card danger">
          <div class="stat-value">{{ overview.waterAlarms || 0 }}</div>
          <div class="stat-label">水位预警</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card danger">
          <div class="stat-value">{{ overview.intrusionAlarms || 0 }}</div>
          <div class="stat-label">入侵预警</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts Row 1: Alert Type + Device Status + Alert Trend -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="never">
          <template #header><span>预警类型分布</span></template>
          <div ref="alertTypeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="never">
          <template #header><span>设备状态</span></template>
          <div ref="deviceStatusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="8">
        <el-card shadow="never">
          <template #header><span>今日预警趋势</span></template>
          <div ref="alertTrendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Device Status + Water Trend Chart -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :sm="24" :md="10">
        <el-card shadow="never">
          <template #header>
            <span>设备实时状态</span>
            <el-button type="primary" link style="float: right" @click="loadData">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </template>
          <div v-for="device in devices" :key="device.id" class="device-card" :class="{ offline: device.status !== 1 }">
            <div class="device-header">
              <div>
                <span class="device-id">{{ device.id }}</span>
                <el-tag :type="device.status === 1 ? 'success' : 'info'" size="small" style="margin-left: 8px">
                  {{ device.status === 1 ? '在线' : '离线' }}
                </el-tag>
              </div>
              <span class="device-location">{{ device.location }}</span>
            </div>
            <div class="device-data" v-if="realtimeData[device.id]">
              <div class="data-item">
                <span class="data-label">当前水位</span>
                <span class="data-value" :style="{ color: getAlertLevel(realtimeData[device.id].waterData?.waterLevel, device.waterThreshold).color, fontWeight: 'bold' }">
                  {{ realtimeData[device.id].waterData?.waterLevel ?? '--' }} cm
                  <el-tag :type="getAlertTagType(getAlertLevel(realtimeData[device.id].waterData?.waterLevel, device.waterThreshold))" size="small" style="margin-left: 4px">
                    {{ getAlertLevel(realtimeData[device.id].waterData?.waterLevel, device.waterThreshold).label }}
                  </el-tag>
                </span>
              </div>
              <div class="data-item">
                <span class="data-label">人员入侵</span>
                <span class="data-value" :class="{ danger: realtimeData[device.id].intrusion?.humanDetected === 1 }">
                  {{ realtimeData[device.id].intrusion?.humanDetected === 1 ? '检测到人员' : '正常' }}
                </span>
              </div>
              <div class="data-item">
                <span class="data-label">报警阈值</span>
                <span class="data-value">{{ device.waterThreshold ?? '--' }} cm</span>
              </div>
              <div class="data-item">
                <span class="data-label">电池电压</span>
                <span class="data-value">{{ realtimeData[device.id].waterData?.batteryVoltage ?? '--' }} V</span>
              </div>
            </div>
            <div class="device-data" v-else style="text-align: center; color: #c0c4cc; padding: 12px">
              暂无数据
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="14">
        <el-card shadow="never">
          <template #header>
            <span>水位趋势 (近24小时)</span>
            <el-select v-model="selectedDevice" size="small" style="float: right; width: 150px" @change="loadTrend">
              <el-option v-for="d in devices" :key="d.id" :label="d.location" :value="d.id" />
            </el-select>
          </template>
          <div ref="chartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Recent Alerts -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>最近预警</span>
        <div style="float: right; display: flex; gap: 8px">
          <el-button type="warning" plain size="small" @click="$router.push('/bigscreen')">数据大屏</el-button>
          <el-button type="primary" link @click="$router.push('/alerts')">查看全部</el-button>
        </div>
      </template>
      <div class="table-wrapper">
        <el-table :data="recentAlerts" stripe size="small" v-loading="alertLoading">
          <el-table-column prop="deviceId" label="设备" width="100" />
          <el-table-column prop="deviceLocation" label="位置" min-width="100" />
          <el-table-column prop="alertType" label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.alertType === 'WATER_ALARM' ? 'danger' : 'warning'" size="small">
                {{ row.alertType === 'WATER_ALARM' ? '水位' : '入侵' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="waterLevel" label="水位(cm)" width="90">
            <template #default="{ row }">{{ row.waterLevel ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="alertTime" label="触发时间" width="160" />
          <el-table-column prop="isHandled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isHandled === 1 ? 'success' : 'danger'" size="small">
                {{ row.isHandled === 1 ? '已处理' : '未处理' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDevices, getRealtimeData, getOverview, getAlerts, getWaterTrend } from '../api'
import { useAlertStore } from '../store'
import { getAlertLevel, getAlertTagType } from '../utils/alertLevel'

const alertStore = useAlertStore()
const devices = ref([])
const realtimeData = ref({})
const overview = ref({})
const recentAlerts = ref([])
const alertLoading = ref(false)
const selectedDevice = ref('')
const chartRef = ref()
const alertTypeChartRef = ref()
const deviceStatusChartRef = ref()
const alertTrendChartRef = ref()
let chartInstance = null
let alertTypeChart = null
let deviceStatusChart = null
let alertTrendChart = null
let refreshTimer = null

const isOverThreshold = (device, data) => {
  if (!device.waterThreshold || !data?.waterData?.waterLevel) return false
  return Number(data.waterData.waterLevel) > Number(device.waterThreshold)
}

const loadOverview = async () => {
  try {
    const res = await getOverview()
    overview.value = res.data || {}
  } catch (e) {}
}

const loadDevices = async () => {
  try {
    const res = await getDevices()
    devices.value = res.data || []
    if (devices.value.length > 0 && !selectedDevice.value) {
      selectedDevice.value = devices.value[0].id
    }
    for (const device of devices.value) {
      try {
        const dataRes = await getRealtimeData(device.id)
        realtimeData.value[device.id] = dataRes.data || {}
      } catch (e) {}
    }
  } catch (e) {}
}

const loadAlerts = async () => {
  alertLoading.value = true
  try {
    const res = await getAlerts({ page: 1, size: 5 })
    recentAlerts.value = res.data?.records || []
  } catch (e) {}
  alertLoading.value = false
}

// 预警类型分布饼图
const loadAlertTypeChart = async () => {
  await nextTick()
  if (!alertTypeChartRef.value) return
  if (!alertTypeChart) {
    alertTypeChart = echarts.init(alertTypeChartRef.value)
    window.addEventListener('resize', () => alertTypeChart?.resize())
  }
  const waterAlarms = overview.value.waterAlarms || 0
  const intrusionAlarms = overview.value.intrusionAlarms || 0
  alertTypeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    color: ['#f56c6c', '#e6a23c'],
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{c}次' },
      data: [
        { value: waterAlarms, name: '水位预警' },
        { value: intrusionAlarms, name: '入侵预警' }
      ]
    }]
  })
}

// 设备状态饼图
const loadDeviceStatusChart = async () => {
  await nextTick()
  if (!deviceStatusChartRef.value) return
  if (!deviceStatusChart) {
    deviceStatusChart = echarts.init(deviceStatusChartRef.value)
    window.addEventListener('resize', () => deviceStatusChart?.resize())
  }
  const online = overview.value.onlineDevices || 0
  const total = overview.value.totalDevices || 0
  const offline = total - online
  deviceStatusChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 台 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    color: ['#67c23a', '#909399'],
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{c}台' },
      data: [
        { value: online, name: '在线' },
        { value: offline, name: '离线' }
      ]
    }]
  })
}

// 今日预警趋势（按小时）
const loadAlertTrendChart = async () => {
  await nextTick()
  if (!alertTrendChartRef.value) return
  if (!alertTrendChart) {
    alertTrendChart = echarts.init(alertTrendChartRef.value)
    window.addEventListener('resize', () => alertTrendChart?.resize())
  }

  try {
    // 获取今天的预警（最多100条）
    const res = await getAlerts({ page: 1, size: 100 })
    const alerts = res.data?.records || []

    // 按小时统计
    const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`)
    const waterByHour = new Array(24).fill(0)
    const intrusionByHour = new Array(24).fill(0)

    const today = new Date().toISOString().substring(0, 10)
    alerts.forEach(a => {
      if (a.alertTime && a.alertTime.startsWith(today)) {
        const hour = parseInt(a.alertTime.substring(11, 13))
        if (a.alertType === 'WATER_ALARM') waterByHour[hour]++
        else intrusionByHour[hour]++
      }
    })

    alertTrendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      color: ['#f56c6c', '#e6a23c'],
      grid: { left: 35, right: 10, bottom: 35, top: 15 },
      xAxis: {
        type: 'category',
        data: hours,
        axisLabel: { fontSize: 10, interval: 3 }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { fontSize: 10 }
      },
      series: [
        { name: '水位', type: 'bar', stack: 'total', data: waterByHour, barWidth: '60%' },
        { name: '入侵', type: 'bar', stack: 'total', data: intrusionByHour, barWidth: '60%' }
      ]
    })
  } catch (e) {}
}

const loadTrend = async () => {
  if (!selectedDevice.value || !chartRef.value) return
  await nextTick()
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
    window.addEventListener('resize', () => chartInstance?.resize())
  }
  try {
    const res = await getWaterTrend(selectedDevice.value, 24)
    const data = res.data || []
    const threshold = devices.value.find(d => d.id === selectedDevice.value)?.waterThreshold || 50
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map(d => d.collectTime?.substring(11, 16) || ''), axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', name: '水位(cm)', min: function(v) { return Math.max(0, v.min - 5) } },
      series: [{
        type: 'line',
        data: data.map(d => d.waterLevel),
        smooth: true,
        areaStyle: { opacity: 0.15 },
        lineStyle: { width: 2 },
        itemStyle: { color: '#1a73e8' },
        markLine: {
          silent: true,
          data: [
            { yAxis: threshold, lineStyle: { color: '#f56c6c', type: 'dashed' }, label: { formatter: '危险', color: '#f56c6c' } },
            { yAxis: threshold * 0.85, lineStyle: { color: '#e6a23c', type: 'dotted' }, label: { formatter: '警告', color: '#e6a23c' } },
            { yAxis: threshold * 0.7, lineStyle: { color: '#409eff', type: 'dotted' }, label: { formatter: '注意', color: '#409eff' } }
          ]
        }
      }],
      grid: { left: 40, right: 20, bottom: 30, top: 40 },
      dataZoom: [{ type: 'inside' }]
    })
  } catch (e) {}
}

let isLoading = false

const loadData = async () => {
  if (isLoading) return  // 防止并发加载
  isLoading = true
  try {
    await loadOverview()
    await loadDevices()
    await loadAlerts()
    await loadTrend()
    // 新增图表（不阻塞主流程）
    loadAlertTypeChart()
    loadDeviceStatusChart()
    loadAlertTrendChart()
  } finally {
    isLoading = false
  }
}

onMounted(() => {
  loadData()
  refreshTimer = setInterval(loadData, 30000)  // 30秒刷新一次
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (chartInstance) {
    window.removeEventListener('resize', chartInstance.resize)
    chartInstance.dispose()
  }
  if (alertTypeChart) {
    window.removeEventListener('resize', alertTypeChart.resize)
    alertTypeChart.dispose()
  }
  if (deviceStatusChart) {
    window.removeEventListener('resize', deviceStatusChart.resize)
    deviceStatusChart.dispose()
  }
  if (alertTrendChart) {
    window.removeEventListener('resize', alertTrendChart.resize)
    alertTrendChart.dispose()
  }
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 统计卡片 */
.stat-row {
  margin-bottom: 0;
}

.stat-card {
  text-align: center;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  position: relative;
  overflow: hidden;
  transition: box-shadow 0.25s, background 0.25s;
}
.stat-card:hover {
  box-shadow: 0 4px 20px rgba(56, 97, 251, 0.08);
  background: rgba(255, 255, 255, 0.85);
}

.stat-card :deep(.el-card__body) {
  padding: 20px 12px;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #dcdfe6;
}

.stat-card.online::before { background: linear-gradient(90deg, #67c23a, #95d475); }
.stat-card.info::before { background: linear-gradient(90deg, #3861fb, #6b8aff); }
.stat-card.warning::before { background: linear-gradient(90deg, #e6a23c, #f0c78a); }
.stat-card.danger::before { background: linear-gradient(90deg, #f56c6c, #f89898); }

.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: #1a1a1a;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

/* 图表卡片 */
.el-card {
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.el-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* 设备列表 */
.device-card {
  padding: 14px 16px;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;
}

.device-card:hover {
  background: rgba(56, 97, 251, 0.04);
}

.device-card:last-child { border-bottom: none; }
.device-card.offline { opacity: 0.5; }

.device-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.device-id {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}

.device-location {
  font-size: 12px;
  color: #909399;
}

.device-data {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 20px;
}

.data-item {
  display: flex;
  justify-content: space-between;
  padding: 3px 0;
}

.data-label {
  color: #909399;
  font-size: 12px;
}

.data-value {
  font-weight: 500;
  font-size: 13px;
  color: #303133;
}

.data-value.danger {
  color: #f56c6c;
  font-weight: 600;
}

/* 图表容器 */
.chart-container {
  height: 300px;
}

/* 表格 */
.table-wrapper {
  overflow-x: auto;
}

/* 响应式 */
@media (max-width: 768px) {
  .stat-value { font-size: 24px; }
  .stat-label { font-size: 12px; }
  .chart-container { height: 240px; }
}

@media (max-width: 480px) {
  .stat-value { font-size: 20px; }
  .stat-label { font-size: 11px; }
  .chart-container { height: 200px; }
}
</style>
