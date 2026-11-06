<template>
  <div class="devices-page">
    <el-card shadow="never">
      <template #header>
        <span>设备管理</span>
        <el-button type="primary" style="float: right" @click="openForm()">添加设备</el-button>
      </template>

      <div class="table-wrapper">
        <el-table :data="devices" v-loading="loading" stripe>
          <el-table-column prop="id" label="设备编号" width="110" />
          <el-table-column prop="location" label="部署位置" min-width="120" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="longitude" label="经度" width="100">
            <template #default="{ row }">{{ row.longitude ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="latitude" label="纬度" width="100">
            <template #default="{ row }">{{ row.latitude ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="waterThreshold" label="水位阈值(cm)" width="110">
            <template #default="{ row }">{{ row.waterThreshold ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="lastHeartbeat" label="最后心跳" width="160">
            <template #default="{ row }">{{ row.lastHeartbeat ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="视频" width="90">
            <template #default="{ row }">
              <el-tag :type="row.streamUrl ? 'success' : 'info'" size="small">
                {{ row.streamUrl ? '已配置' : '未配置' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="installedDate" label="安装日期" width="110" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openForm(row)">编辑</el-button>
              <el-button type="warning" link @click="openThreshold(row)">阈值</el-button>
              <el-popconfirm title="确认删除该设备？" @confirm="deleteDeviceItem(row.id)">
                <template #reference>
                  <el-button type="danger" link>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- Add/Edit Dialog -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑设备' : '添加设备'" :width="dialogWidth">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备编号" prop="id">
          <el-input v-model="form.id" :disabled="isEdit" placeholder="如 DEV-001" />
        </el-form-item>
        <el-form-item label="部署位置" prop="location">
          <el-input v-model="form.location" placeholder="如 东湖公园1号监测点" />
        </el-form-item>
        <el-form-item label="水位阈值">
          <el-input-number v-model="form.waterThreshold" :min="0" :max="999" :precision="2" placeholder="cm" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="经度">
              <el-input-number v-model="form.longitude" :min="73" :max="136" :precision="6" :step="0.001" placeholder="如 114.305" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度">
              <el-input-number v-model="form.latitude" :min="3" :max="54" :precision="6" :step="0.001" placeholder="如 30.592" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" link @click="getLocation">
            <el-icon><Location /></el-icon> 获取当前位置
          </el-button>
          <span style="font-size: 12px; color: #707070; margin-left: 8px">点击自动填入当前经纬度</span>
        </el-form-item>
        <el-form-item label="视频流地址">
          <el-input
            v-model="form.streamUrl"
            placeholder="留空 = 用全局配置；如 https://xxx.trycloudflare.com/stream"
            clearable
          />
          <div style="font-size: 12px; color: #707070; line-height: 1.5; margin-top: 4px">
            这个点位摄像头的画面地址。家里跑隧道的话填隧道地址，
            同局域网也可以填 http://192.168.31.77:8082/stream
          </div>
        </el-form-item>
        <el-form-item label="安装日期">
          <el-date-picker v-model="form.installedDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- Threshold Dialog -->
    <el-dialog v-model="thresholdVisible" title="设置水位阈值" :width="dialogWidth">
      <el-form label-width="80px">
        <el-form-item label="设备">{{ currentDevice?.id }} - {{ currentDevice?.location }}</el-form-item>
        <el-form-item label="阈值(cm)">
          <el-input-number v-model="thresholdValue" :min="0" :max="999" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="thresholdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitThreshold">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDevices, addDevice, updateDevice, deleteDevice, updateThreshold } from '../api'

const loading = ref(false)
const devices = ref([])
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const submitLoading = ref(false)
const currentEditId = ref('')

const thresholdVisible = ref(false)
const currentDevice = ref(null)
const thresholdValue = ref(50)
const dialogWidth = window.innerWidth < 768 ? '95%' : '550px'

const form = reactive({ id: '', location: '', waterThreshold: 50, longitude: null, latitude: null, installedDate: '', description: '', streamUrl: '' })
const rules = {
  id: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  location: [{ required: true, message: '请输入部署位置', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getDevices()
    devices.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openForm = (row) => {
  isEdit.value = !!row
  if (row) {
    currentEditId.value = row.id
    Object.assign(form, { id: row.id, location: row.location, waterThreshold: row.waterThreshold, longitude: row.longitude, latitude: row.latitude, installedDate: row.installedDate, description: row.description, streamUrl: row.streamUrl || '' })
  } else {
    Object.assign(form, { id: '', location: '', waterThreshold: 50, longitude: null, latitude: null, installedDate: '', description: '', streamUrl: '' })
  }
  formVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateDevice(currentEditId.value, form)
      ElMessage.success('更新成功')
    } else {
      await addDevice(form)
      ElMessage.success('添加成功')
    }
    formVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

const deleteDeviceItem = async (id) => {
  await deleteDevice(id)
  ElMessage.success('删除成功')
  loadData()
}

const openThreshold = (row) => {
  currentDevice.value = row
  thresholdValue.value = row.waterThreshold || 50
  thresholdVisible.value = true
}

const getLocation = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('浏览器不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      form.longitude = Number(pos.coords.longitude.toFixed(6))
      form.latitude = Number(pos.coords.latitude.toFixed(6))
      ElMessage.success('定位成功')
    },
    (err) => {
      ElMessage.error('定位失败: ' + err.message)
    }
  )
}

const submitThreshold = async () => {
  await updateThreshold(currentDevice.value.id, { waterThreshold: thresholdValue.value })
  ElMessage.success('阈值已更新')
  thresholdVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.table-wrapper {
  overflow-x: auto;
}
</style>
