<template>
  <div class="alerts-page">
    <!-- Filters -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="未处理" value="pending" />
            <el-option label="已处理" value="handled" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="query.deviceId" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="d in devices" :key="d.id" :label="d.location" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button type="success" plain @click="exportExcel">导出 Excel</el-button>
          <el-button type="danger" plain @click="handleClear">清空已处理</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="never">
      <div class="table-wrapper">
        <el-table :data="alerts" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="deviceId" label="设备" width="100" />
          <el-table-column prop="deviceLocation" label="位置" min-width="120" show-overflow-tooltip />
          <el-table-column prop="alertType" label="预警类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.alertType === 'WATER_ALARM' ? 'danger' : 'warning'" size="small">
                {{ row.alertType === 'WATER_ALARM' ? '水位' : '入侵' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="waterLevel" label="水位(cm)" width="90">
            <template #default="{ row }">
              {{ row.waterLevel ?? '--' }}
            </template>
          </el-table-column>
          <el-table-column prop="alertTime" label="触发时间" width="160" />
          <el-table-column prop="isHandled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.isHandled === 1 ? 'success' : 'danger'" size="small">
                {{ row.isHandled === 1 ? '已处理' : '未处理' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="handlerName" label="处理人" width="90">
            <template #default="{ row }">{{ row.handlerName || '--' }}</template>
          </el-table-column>
          <el-table-column prop="handleNote" label="处置备注" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.handleNote || '--' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.isHandled === 0" type="primary" link @click="openHandle(row)">处理</el-button>
              <span v-else style="color: #707070; margin-right: 8px">已闭环</span>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-pagination
        class="pagination"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- Handle Dialog -->
    <el-dialog v-model="handleDialogVisible" title="预警处置" :width="dialogWidth">
      <el-form :model="handleForm" label-width="80px">
        <el-form-item label="预警信息">
          <el-tag :type="currentAlert?.alertType === 'WATER_ALARM' ? 'danger' : 'warning'">
            {{ currentAlert?.alertType === 'WATER_ALARM' ? '水位超限' : '人员入侵' }}
          </el-tag>
          <span style="margin-left: 8px">{{ currentAlert?.deviceId }} - {{ currentAlert?.deviceLocation }}</span>
        </el-form-item>
        <el-form-item label="处置备注" required>
          <el-input v-model="handleForm.handleNote" type="textarea" :rows="3" placeholder="请输入处置情况说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleLoading" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { getAlerts, handleAlert, getDevices, deleteAlert, clearAlerts } from '../api'
import { useAlertStore } from '../store'

const alertStore = useAlertStore()
const loading = ref(false)
const alerts = ref([])
const total = ref(0)
const devices = ref([])

const query = reactive({ status: '', deviceId: '', page: 1, size: 10 })

const handleDialogVisible = ref(false)
const handleLoading = ref(false)
const currentAlert = ref(null)
const handleForm = reactive({ handleNote: '' })
const dialogWidth = window.innerWidth < 768 ? '95%' : '500px'

const loadData = async () => {
  loading.value = true
  try {
    const res = await getAlerts(query)
    alerts.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const loadDevices = async () => {
  const res = await getDevices()
  devices.value = res.data || []
}

const openHandle = (row) => {
  currentAlert.value = row
  handleForm.handleNote = ''
  handleDialogVisible.value = true
}

const submitHandle = async () => {
  if (!handleForm.handleNote.trim()) {
    ElMessage.warning('请填写处置备注')
    return
  }
  handleLoading.value = true
  try {
    await handleAlert(currentAlert.value.id, handleForm)
    ElMessage.success('处理成功')
    handleDialogVisible.value = false
    loadData()
    alertStore.refreshCount()
  } finally {
    handleLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除这条预警记录？', '提示', { type: 'warning' })
    await deleteAlert(row.id)
    ElMessage.success('删除成功')
    loadData()
    alertStore.refreshCount()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const exportExcel = async () => {
  try {
    // Fetch all alerts (up to 1000)
    const res = await getAlerts({ ...query, page: 1, size: 1000 })
    const data = (res.data?.records || []).map(row => ({
      'ID': row.id,
      '设备ID': row.deviceId,
      '设备位置': row.deviceLocation || '',
      '预警类型': row.alertType === 'WATER_ALARM' ? '水位预警' : '入侵预警',
      '水位(cm)': row.waterLevel ?? '',
      '触发时间': row.alertTime || '',
      '状态': row.isHandled === 1 ? '已处理' : '未处理',
      '处理人': row.handlerName || '',
      '处置备注': row.handleNote || ''
    }))
    if (data.length === 0) {
      ElMessage.warning('没有数据可导出')
      return
    }
    const ws = XLSX.utils.json_to_sheet(data)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '预警记录')
    // Set column widths
    ws['!cols'] = [{ wch: 8 }, { wch: 12 }, { wch: 18 }, { wch: 12 }, { wch: 10 }, { wch: 20 }, { wch: 10 }, { wch: 10 }, { wch: 30 }]
    XLSX.writeFile(wb, `预警记录_${new Date().toISOString().substring(0, 10)}.xlsx`)
    ElMessage.success(`导出 ${data.length} 条记录`)
  } catch (e) {
    ElMessage.error('导出失败')
    console.error(e)
  }
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有已处理的预警记录？此操作不可恢复。', '警告', { type: 'warning' })
    await clearAlerts('handled')
    ElMessage.success('清空成功')
    loadData()
    alertStore.refreshCount()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(() => {
  loadData()
  loadDevices()
})
</script>

<style scoped>
.table-wrapper {
  overflow-x: auto;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
