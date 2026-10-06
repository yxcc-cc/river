<template>
  <div class="map-page">
    <el-row :gutter="16">
      <!-- Map Area -->
      <el-col :xs="24" :lg="17">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span><el-icon><Location /></el-icon> 监测点分布地图</span>
              <div style="display: flex; gap: 8px; align-items: center">
                <span class="legend"><span class="dot dot-green"></span> 正常</span>
                <span class="legend"><span class="dot dot-blue"></span> 注意</span>
                <span class="legend"><span class="dot dot-orange"></span> 警告</span>
                <span class="legend"><span class="dot dot-red"></span> 危险</span>
                <span class="legend"><span class="dot dot-gray"></span> 离线</span>
              </div>
            </div>
          </template>
          <div ref="mapContainer" class="map-wrapper"></div>
        </el-card>
      </el-col>

      <!-- Device Panel -->
      <el-col :xs="24" :lg="7">
        <el-card shadow="never">
          <template #header>
            <span><el-icon><Monitor /></el-icon> 设备实时状态</span>
          </template>
          <div class="device-list">
            <div
              v-for="device in devices"
              :key="device.id"
              class="device-card"
              :class="'level-' + (device.levelKey || 'normal')"
              @click="focusDevice(device)"
            >
              <div class="device-header">
                <span class="device-name">{{ device.location || device.id }}</span>
                <el-tag :type="device.status === 1 ? 'success' : 'info'" size="small">
                  {{ device.status === 1 ? '在线' : '离线' }}
                </el-tag>
              </div>
              <div class="device-data">
                <div class="data-row">
                  <span class="data-label">水位</span>
                  <span class="data-value" :style="{ color: device.levelColor || '#67c23a' }">
                    {{ device.waterLevel ?? '--' }} cm
                    <el-tag v-if="device.levelLabel" :type="device.levelTagType" size="small" style="margin-left:4px">
                      {{ device.levelLabel }}
                    </el-tag>
                  </span>
                </div>
                <div class="data-row">
                  <span class="data-label">入侵</span>
                  <span class="data-value" :style="{ color: device.intrusion ? '#f56c6c' : '#67c23a' }">
                    {{ device.intrusion ? '⚠ 检测到人员' : '正常' }}
                  </span>
                </div>
                <div class="data-row">
                  <span class="data-label">阈值</span>
                  <span class="data-value">{{ device.waterThreshold ?? '--' }} cm</span>
                </div>
                <div class="data-row">
                  <span class="data-label">坐标</span>
                  <span class="data-value coord">{{ device.longitude }}, {{ device.latitude }}</span>
                </div>
              </div>
              <!-- Water level progress bar -->
              <div class="water-bar-bg">
                <div
                  class="water-bar-fill"
                  :style="{ width: getWaterPercent(device), background: device.levelColor || '#67c23a' }"
                ></div>
              </div>
              <div class="device-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  @click.stop="goMonitor(device)"
                >
                  <el-icon><VideoCamera /></el-icon> 查看监控
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { VideoCamera } from '@element-plus/icons-vue'
import { getDevices, getRealtimeData } from '../api'
import { getAlertLevel, getAlertTagType } from '../utils/alertLevel'

const router = useRouter()

const goMonitor = (device) => {
  router.push({ path: '/monitor', query: { dev: device.id } })
}

// InfoWindow中的跳转（AMap InfoWindow内JS无法直接调用Vue router）
window.__goMonitor = (devId) => {
  router.push({ path: '/monitor', query: { dev: devId } })
}

const mapContainer = ref(null)
const devices = ref([])
let map = null
let refreshTimer = null
// Store marker references for updating
const markerMap = {}
const circleMap = {}

const getWaterPercent = (device) => {
  const wl = device.waterLevel
  const threshold = device.waterThreshold
  if (!wl || !threshold) return '0%'
  return Math.min(100, (wl / threshold) * 100) + '%'
}

const getMarkerIcon = (device) => {
  const colors = {
    normal: '#67c23a',
    notice: '#409eff',
    warning: '#e6a23c',
    danger: '#f56c6c'
  }
  const color = device.status === 1 ? (colors[device.levelKey] || '#67c23a') : '#909399'
  const size = device.levelKey === 'danger' ? 36 : 30

  return new AMap.Icon({
    size: new AMap.Size(size, size),
    image: `data:image/svg+xml,${encodeURIComponent(
      `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24"><path fill="${color}" d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>`
    )}`,
    imageSize: new AMap.Size(size, size)
  })
}

const loadDevices = async () => {
  try {
    const res = await getDevices()
    const raw = (res.data || []).filter(d => d.longitude != null && d.latitude != null)

    // Load realtime data
    for (const d of raw) {
      try {
        const r = await getRealtimeData(d.id)
        const data = r.data || {}
        d.waterLevel = data.waterData?.waterLevel ?? null
        d.intrusion = data.intrusion?.humanDetected === 1
      } catch (e) {
        d.waterLevel = null
        d.intrusion = false
      }
      const level = getAlertLevel(d.waterLevel, d.waterThreshold)
      d.levelKey = level.key
      d.levelColor = level.color
      d.levelLabel = level.label
      d.levelTagType = getAlertTagType(level)
    }

    devices.value = raw
    initMap()
  } catch (e) {
    console.error('Failed to load devices:', e)
  }
}

const refreshData = async () => {
  for (const d of devices.value) {
    try {
      const r = await getRealtimeData(d.id)
      const data = r.data || {}
      d.waterLevel = data.waterData?.waterLevel ?? null
      d.intrusion = data.intrusion?.humanDetected === 1
      const level = getAlertLevel(d.waterLevel, d.waterThreshold)
      d.levelKey = level.key
      d.levelColor = level.color
      d.levelLabel = level.label
      d.levelTagType = getAlertTagType(level)

      // Update marker icon
      if (markerMap[d.id]) {
        markerMap[d.id].setIcon(getMarkerIcon(d))
      }
      // Update circle color
      if (circleMap[d.id]) {
        circleMap[d.id].setOptions({
          strokeColor: level.color,
          fillColor: level.color
        })
      }
    } catch (e) {}
  }
}

const initMap = () => {
  if (!window.AMap || devices.value.length === 0 || !mapContainer.value) {
    // Retry after DOM is ready
    if (!mapContainer.value) {
      setTimeout(initMap, 200)
    }
    return
  }

  const center = [Number(devices.value[0].longitude), Number(devices.value[0].latitude)]

  map = new AMap.Map(mapContainer.value, {
    zoom: 15,
    center: center,
    resizeEnable: true,
    mapStyle: 'amap://styles/normal'
  })

  devices.value.forEach(device => {
    const lng = Number(device.longitude)
    const lat = Number(device.latitude)
    const pos = [lng, lat]

    // Marker
    const marker = new AMap.Marker({
      position: pos,
      icon: getMarkerIcon(device),
      offset: new AMap.Pixel(-15, -15),
      map: map
    })

    // Info window
    const infoWindow = new AMap.InfoWindow({
      content: `
        <div style="padding:8px;min-width:220px;line-height:1.8">
          <div style="font-weight:bold;font-size:14px;margin-bottom:6px">${device.location || device.id}</div>
          <div><b>设备:</b> ${device.id}</div>
          <div><b>状态:</b> ${device.status === 1 ? '在线' : '离线'}</div>
          <div><b>水位:</b> ${device.waterLevel ?? '--'} cm</div>
          <div><b>入侵:</b> ${device.intrusion ? '⚠ 检测到人员' : '正常'}</div>
          <div><b>阈值:</b> ${device.waterThreshold ?? '--'} cm</div>
          <div><b>坐标:</b> ${lng}, ${lat}</div>
          <div style="margin-top:8px">
            <a href="/monitor?dev=${device.id}" style="color:#3861fb;text-decoration:none;font-weight:500;font-size:13px" onclick="event.preventDefault(); window.__goMonitor && window.__goMonitor('${device.id}')">
              📹 查看实时监控
            </a>
          </div>
        </div>
      `,
      offset: new AMap.Pixel(0, -20)
    })

    marker.on('click', () => {
      infoWindow.open(map, marker.getPosition())
    })

    markerMap[device.id] = marker

    // Monitoring range circle (500m radius)
    const circle = new AMap.Circle({
      center: pos,
      radius: 500,
      strokeColor: device.levelColor || '#67c23a',
      strokeWeight: 2,
      strokeOpacity: 0.6,
      fillColor: device.levelColor || '#67c23a',
      fillOpacity: 0.08,
      map: map
    })

    circleMap[device.id] = circle
  })

  map.addControl(new AMap.Scale())
  map.addControl(new AMap.ToolBar())
}

const focusDevice = (device) => {
  if (!map || !device.longitude) return
  const lng = Number(device.longitude)
  const lat = Number(device.latitude)
  map.setCenter([lng, lat])
  map.setZoom(17)
  if (markerMap[device.id]) {
    markerMap[device.id].emit('click')
  }
}

onMounted(async () => {
  await nextTick()
  if (window.AMap) {
    loadDevices()
  } else {
    const script = document.createElement('script')
    script.src = 'https://webapi.amap.com/maps?v=2.0&key=9cfeb65d9e8b30a0042bbdf1b481127b'
    script.onload = () => {
      nextTick(() => loadDevices())
    }
    document.head.appendChild(script)
  }
  // Refresh every 15 seconds
  refreshTimer = setInterval(refreshData, 15000)
})

onUnmounted(() => {
  clearInterval(refreshTimer)
  if (map) map.destroy()
  delete window.__goMonitor
})
</script>

<style scoped>
.map-page { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.legend { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #606266; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.dot-green { background: #67c23a; }
.dot-blue { background: #409eff; }
.dot-orange { background: #e6a23c; }
.dot-red { background: #f56c6c; }
.dot-gray { background: #909399; }

.map-wrapper { height: 550px; border-radius: 8px; overflow: hidden; }

.device-list { max-height: 600px; overflow-y: auto; }
.device-card {
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  cursor: pointer;
  transition: all 0.25s;
  border-left: 4px solid #67c23a;
}
.device-card:hover { border-color: #409eff; box-shadow: 0 4px 16px rgba(56, 97, 251, 0.08); background: rgba(255, 255, 255, 0.85); }
.device-card.level-notice { border-left-color: #409eff; }
.device-card.level-warning { border-left-color: #e6a23c; }
.device-card.level-danger { border-left-color: #f56c6c; }

.device-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.device-name { font-weight: 500; font-size: 14px; }

.device-data { display: flex; flex-direction: column; gap: 4px; }
.data-row { display: flex; justify-content: space-between; align-items: center; }
.data-label { font-size: 12px; color: #909399; }
.data-value { font-size: 13px; font-weight: 500; }
.data-value.coord { font-size: 11px; color: #909399; font-family: monospace; }

.water-bar-bg {
  margin-top: 8px;
  height: 4px;
  background: #ebeef5;
  border-radius: 2px;
  overflow: hidden;
}
.water-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.5s ease;
}

.device-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>
