<template>
  <div class="attendance-page">
    <!-- 签到区域 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="checkin-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Clock /></el-icon> 人脸签到</span>
              <el-tag :type="faceServiceOnline ? 'success' : 'danger'" size="small">
                {{ faceServiceOnline ? '人脸服务在线' : '人脸服务离线' }}
              </el-tag>
            </div>
          </template>

          <!-- 摄像头预览 -->
          <div class="camera-section">
            <div class="camera-preview">
              <video ref="videoRef" autoplay playsinline class="camera-video"></video>
              <canvas ref="canvasRef" style="display:none"></canvas>
              <div v-if="!cameraReady" class="camera-placeholder">
                <el-icon :size="48" color="#c0c4cc"><VideoCamera /></el-icon>
                <p>点击下方按钮开启摄像头</p>
              </div>
              <!-- 人脸框 -->
              <div v-if="faceDetected" class="face-indicator">
                <el-icon><CircleCheck /></el-icon> 已检测到人脸
              </div>
            </div>
            <div class="camera-actions">
              <el-button @click="toggleCamera" :type="cameraReady ? 'warning' : 'primary'">
                <el-icon><VideoCamera /></el-icon>
                {{ cameraReady ? '关闭摄像头' : '开启摄像头' }}
              </el-button>
            </div>
          </div>

          <!-- 人脸注册状态 -->
          <div class="register-section">
            <div class="register-status">
              <el-tag :type="faceRegistered ? 'success' : 'warning'" size="small">
                <el-icon style="vertical-align: middle; margin-right: 4px">
                  <CircleCheck v-if="faceRegistered" /><Warning v-else />
                </el-icon>
                {{ faceRegistered ? '人脸已注册' : '人脸未注册' }}
              </el-tag>
              <el-button
                type="primary"
                link
                size="small"
                @click="doRegister"
                :disabled="!cameraReady || !faceServiceOnline"
                :loading="registering"
              >
                {{ faceRegistered ? '重新注册' : '注册人脸' }}
              </el-button>
            </div>
            <el-alert
              v-if="!faceRegistered && faceServiceOnline"
              title="请先注册人脸后再进行签到"
              type="warning"
              :closable="false"
              show-icon
              style="margin-top: 8px"
            />
          </div>

          <!-- 签到签退按钮 -->
          <div class="check-buttons">
            <el-button
              type="primary"
              size="large"
              :disabled="!cameraReady || todayStatus.checkedIn"
              :loading="checkingIn"
              @click="doCheckIn"
              class="check-btn"
            >
              <el-icon><Check /></el-icon>
              {{ todayStatus.checkedIn ? '已签到（待签退）' : '签到' }}
            </el-button>
            <el-button
              type="warning"
              size="large"
              :disabled="!cameraReady || todayStatus.checkedOut"
              :loading="checkingOut"
              @click="doCheckOut"
              class="check-btn"
            >
              <el-icon><Close /></el-icon>
              {{ todayStatus.checkedOut ? '已签退（可再次签到）' : '签退' }}
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 今日状态 -->
      <el-col :xs="24" :lg="14">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never" class="status-card">
              <div class="status-icon checkin-icon">
                <el-icon :size="28"><Sunrise /></el-icon>
              </div>
              <div class="status-info">
                <span class="status-label">签到时间</span>
                <span class="status-value">{{ todayStatus.checkInTime || '--:--:--' }}</span>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" class="status-card">
              <div class="status-icon checkout-icon">
                <el-icon :size="28"><Sunset /></el-icon>
              </div>
              <div class="status-info">
                <span class="status-label">签退时间</span>
                <span class="status-value">{{ todayStatus.checkOutTime || '--:--:--' }}</span>
              </div>
            </el-card>
          </el-col>
          <el-col :span="24" style="margin-top: 16px">
            <el-card shadow="never" class="status-card work-hours-card">
              <div class="status-icon work-icon">
                <el-icon :size="28"><Timer /></el-icon>
              </div>
              <div class="status-info">
                <span class="status-label">工作时长</span>
                <span class="status-value">{{ todayStatus.workHours || '未签到' }}</span>
              </div>
            </el-card>
          </el-col>

          <!-- 月度统计 -->
          <el-col :span="24" style="margin-top: 16px">
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span><el-icon><DataAnalysis /></el-icon> 月度统计</span>
                  <el-date-picker
                    v-model="selectedMonth"
                    type="month"
                    size="small"
                    format="YYYY-MM"
                    value-format="YYYY-MM"
                    @change="loadStatistics"
                    style="width: 140px"
                  />
                </div>
              </template>
              <div class="month-stats">
                <div class="stat-item">
                  <span class="stat-num">{{ monthStats.totalDays || 0 }}</span>
                  <span class="stat-label">出勤天数</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num warning">{{ monthStats.lateCount || 0 }}</span>
                  <span class="stat-label">迟到次数</span>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-col>
    </el-row>

    <!-- 考勤记录表格 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span><el-icon><List /></el-icon> 考勤记录</span>
          <div class="filter-bar">
            <el-select
              v-if="isAdmin"
              v-model="filterUserId"
              placeholder="筛选人员"
              clearable
              size="small"
              style="width: 140px"
              @change="loadRecords"
            >
              <el-option
                v-for="u in userList"
                :key="u.id"
                :label="u.realName || u.username"
                :value="u.id"
              />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              size="small"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="loadRecords"
              style="width: 260px"
            />
          </div>
        </div>
      </template>
      <el-table :data="records" stripe style="width: 100%">
        <el-table-column prop="realName" label="姓名" width="100">
          <template #default="{ row }">
            {{ row.realName || row.userName || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'success' : 'warning'" size="small">
              {{ row.type === 1 ? '签到' : '签退' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkTime" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.checkTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="150" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="人脸截图" width="100">
          <template #default="{ row }">
            <el-image
              v-if="row.faceImage"
              :src="getFaceImageUrl(row.faceImage)"
              :preview-src-list="[getFaceImageUrl(row.faceImage)]"
              style="width: 50px; height: 50px; border-radius: 4px"
              fit="cover"
            />
            <span v-else style="color: #c0c4cc">无</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRecords"
          @size-change="loadRecords"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  Clock, VideoCamera, Check, Close, Sunrise, Sunset, Timer,
  DataAnalysis, List, CircleCheck, Warning
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { getUsers, registerFace, checkFaceRegistered } from '../api'

const videoRef = ref(null)
const canvasRef = ref(null)
const cameraReady = ref(false)
const faceDetected = ref(false)
const faceServiceOnline = ref(false)
const faceRegistered = ref(false)
const registering = ref(false)
const checkingIn = ref(false)
const checkingOut = ref(false)

const todayStatus = ref({})
const records = ref([])
const userList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const filterUserId = ref(null)
const dateRange = ref(null)
const selectedMonth = ref(new Date().toISOString().substring(0, 7))
const monthStats = ref({})

let stream = null

const isAdmin = computed(() => {
  const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
  return info.roleKey === 'admin'
})

// 摄像头控制
const toggleCamera = async () => {
  if (cameraReady.value) {
    stopCamera()
  } else {
    await startCamera()
  }
}

const startCamera = async () => {
  try {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      ElMessage.error('当前环境不支持摄像头访问，请确保使用 localhost 访问')
      return
    }
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 640, height: 480, facingMode: 'user' }
    })
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      cameraReady.value = true
    } else {
      ElMessage.error('视频元素未就绪，请刷新页面重试')
    }
  } catch (e) {
    console.error('摄像头错误:', e)
    let msg = '无法访问摄像头'
    if (e.name === 'NotAllowedError') {
      msg = '摄像头权限被拒绝，请在浏览器地址栏右侧允许摄像头权限'
    } else if (e.name === 'NotFoundError') {
      msg = '未找到摄像头设备，请检查摄像头是否已启用'
    } else if (e.name === 'NotReadableError') {
      msg = '摄像头被其他程序占用，请关闭其他使用摄像头的程序'
    } else {
      msg += ': ' + (e.message || e.name || JSON.stringify(e))
    }
    ElMessage.error(msg)
  }
}

const stopCamera = () => {
  if (stream) {
    stream.getTracks().forEach(t => t.stop())
    stream = null
  }
  cameraReady.value = false
}

// 拍照并获取base64
const captureImage = () => {
  const video = videoRef.value
  const canvas = canvasRef.value
  if (!video || !canvas) return null
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  canvas.getContext('2d').drawImage(video, 0, 0)
  return canvas.toDataURL('image/jpeg', 0.8)
}

// 检查人脸注册状态
const checkRegistrationStatus = async () => {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    if (!info.userId) return
    const res = await checkFaceRegistered(info.userId)
    faceRegistered.value = res.data?.registered || false
  } catch {
    faceRegistered.value = false
  }
}

// 注册人脸
const doRegister = async () => {
  if (!cameraReady.value) {
    ElMessage.warning('请先开启摄像头')
    return
  }
  registering.value = true
  try {
    const image = captureImage()
    if (!image) {
      ElMessage.error('拍照失败，请重试')
      return
    }
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const res = await registerFace(info.userId, image)
    if (res.code === 200) {
      ElMessage.success('人脸注册成功！')
      faceRegistered.value = true
    } else {
      ElMessage.error(res.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error('注册失败: ' + (e.message || '未知错误'))
  } finally {
    registering.value = false
  }
}

// 获取当前登录用户ID
const getCurrentUserId = () => {
  const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
  return info.userId
}

// 人脸验证（签到签退共用）
const verifyFace = async () => {
  const image = captureImage()
  if (!image) {
    ElMessage.error('请先开启摄像头')
    return null
  }

  const verifyRes = await fetch('http://localhost:8084/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ image })
  })
  const verifyData = await verifyRes.json()

  if (verifyData.code !== 200 || !verifyData.data?.matched) {
    if (verifyData.data?.registered === false) {
      ElMessage.warning('尚未注册人脸，请先注册')
    } else {
      ElMessage.warning(verifyData.message || '人脸验证失败，请确保正对摄像头')
    }
    return null
  }

  // 校验置信度（低于0.85直接拒绝，防止不同人误匹配）
  if (verifyData.data.confidence < 0.85) {
    ElMessage.warning(`人脸置信度过低(${verifyData.data.confidence})，请确保正对摄像头`)
    return null
  }

  // 校验：人脸匹配到的用户 必须是 当前登录用户
  const currentUserId = getCurrentUserId()
  if (verifyData.data.userId !== currentUserId) {
    ElMessage.error('人脸识别与当前登录账号不匹配，请确认是本人操作')
    return null
  }

  return verifyData.data
}

// 签到
const doCheckIn = async () => {
  checkingIn.value = true
  try {
    const faceData = await verifyFace()
    if (!faceData) return

    await request.post('/attendance/check-in', {
      faceImage: faceData.capturePath,
      remark: `人脸签到 (置信度: ${faceData.confidence})`
    })

    ElMessage.success('签到成功！欢迎回来')
    loadTodayStatus()
    loadRecords()
  } catch (e) {
    ElMessage.error('签到失败: ' + (e.message || '未知错误'))
  } finally {
    checkingIn.value = false
  }
}

// 签退
const doCheckOut = async () => {
  checkingOut.value = true
  try {
    const faceData = await verifyFace()
    if (!faceData) return

    await request.post('/attendance/check-out', {
      faceImage: faceData.capturePath,
      remark: `人脸签退 (置信度: ${faceData.confidence})`
    })

    ElMessage.success('签退成功！辛苦了')
    loadTodayStatus()
    loadRecords()
  } catch (e) {
    ElMessage.error('签退失败: ' + (e.message || '未知错误'))
  } finally {
    checkingOut.value = false
  }
}

// 加载今日状态
const loadTodayStatus = async () => {
  try {
    const res = await request.get('/attendance/today')
    todayStatus.value = res.data || {}
  } catch (e) {
    console.error('加载今日状态失败:', e)
  }
}

// 加载考勤记录
const loadRecords = async () => {
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (filterUserId.value) params.userId = filterUserId.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await request.get('/attendance/records', { params })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('加载考勤记录失败:', e)
  }
}

// 加载月度统计
const loadStatistics = async () => {
  try {
    const params = { month: selectedMonth.value }
    if (filterUserId.value) params.userId = filterUserId.value
    const res = await request.get('/attendance/statistics', { params })
    monthStats.value = res.data || {}
  } catch (e) {
    console.error('加载月度统计失败:', e)
  }
}

// 加载用户列表（管理员用）
const loadUsers = async () => {
  if (!isAdmin.value) return
  try {
    const res = await getUsers({ page: 1, size: 100 })
    userList.value = res.data?.records || []
  } catch (e) {
    console.error('加载用户列表失败:', e)
  }
}

// 检查人脸服务状态
const checkFaceService = async () => {
  try {
    const res = await fetch('http://localhost:8084/status')
    const data = await res.json()
    faceServiceOnline.value = data.status === 'running'
  } catch {
    faceServiceOnline.value = false
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return '--'
  return timeStr.replace('T', ' ')
}

const getFaceImageUrl = (path) => {
  if (!path) return ''
  // 从完整路径提取相对路径
  const idx = path.indexOf('uploads')
  if (idx >= 0) return '/' + path.substring(idx)
  return path
}

onMounted(() => {
  checkFaceService()
  checkRegistrationStatus()
  loadTodayStatus()
  loadRecords()
  loadStatistics()
  loadUsers()
})

onUnmounted(() => {
  stopCamera()
})
</script>

<style scoped>
.attendance-page { padding: 0; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.checkin-card { margin-bottom: 0; }

.camera-section { margin-bottom: 16px; }

.camera-preview {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.camera-video {
  width: 100%;
  display: block;
  border-radius: 8px;
}

.camera-placeholder {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.camera-actions {
  margin-top: 8px;
  text-align: center;
}

.face-indicator {
  position: absolute;
  top: 12px;
  right: 12px;
  background: rgba(103, 194, 58, 0.9);
  color: #fff;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
}

.register-section {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.register-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.check-buttons {
  display: flex;
  gap: 16px;
}

.check-btn {
  flex: 1;
  height: 56px;
  font-size: 16px;
  border-radius: 12px;
}

/* Status cards */
.status-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.status-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.checkin-icon { background: rgba(103, 194, 58, 0.1); color: #67c23a; }
.checkout-icon { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.work-icon { background: rgba(56, 97, 251, 0.1); color: #3861fb; }

.status-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-label {
  font-size: 13px;
  color: #909399;
}

.status-value {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

/* Month stats */
.month-stats {
  display: flex;
  gap: 32px;
  justify-content: center;
  padding: 8px 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #3861fb;
}

.stat-num.warning { color: #e6a23c; }

.stat-label {
  font-size: 13px;
  color: #909399;
}

/* Filter bar */
.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* Pagination */
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
