<template>
  <div class="data-history">
    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form inline>
        <el-form-item label="设备">
          <el-select v-model="query.deviceId" placeholder="请选择设备" style="width: 200px" @change="onDeviceChange">
            <el-option v-for="d in devices" :key="d.id" :label="d.location" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="loadTrend">趋势图</el-button>
          <el-button type="success" plain @click="exportExcel">导出 Excel</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Trend Chart -->
    <el-card shadow="never" style="margin-bottom: 16px" v-if="showChart">
      <template #header>水位趋势图 (近24小时)</template>
      <div ref="chartRef" style="height: 300px"></div>
    </el-card>

    <!-- Data Table -->
    <el-card shadow="never">
      <div class="table-wrapper">
        <el-table :data="tableData" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="deviceId" label="设备" width="100" />
          <el-table-column prop="waterLevel" label="水位(cm)" width="100">
            <template #default="{ row }">
              <span :style="{ color: row.waterLevel > 50 ? '#f56c6c' : '' }">{{ row.waterLevel }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="batteryVoltage" label="电池电压(V)" width="110">
            <template #default="{ row }">{{ row.batteryVoltage ?? '--' }}</template>
          </el-table-column>
          <el-table-column prop="collectTime" label="采集时间" min-width="150" />
        </el-table>
      </div>

      <el-pagination
        class="pagination"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import * as XLSX from 'xlsx'
import { ElMessage } from 'element-plus'
import { getDevices, getWaterHistory, getWaterTrend } from '../api'

const devices = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref([])
const showChart = ref(false)
const chartRef = ref()
let chartInstance = null

const query = reactive({ deviceId: '', page: 1, size: 20 })

const loadDevices = async () => {
  const res = await getDevices()
  devices.value = res.data || []
  if (devices.value.length > 0) {
    query.deviceId = devices.value[0].id
  }
}

const loadData = async () => {
  if (!query.deviceId) return
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      params.start = dateRange.value[0]
      params.end = dateRange.value[1]
    }
    const res = await getWaterHistory(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const loadTrend = async () => {
  if (!query.deviceId) return
  showChart.value = true
  await nextTick()

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  try {
    const res = await getWaterTrend(query.deviceId, 24)
    const data = res.data || []
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map(d => d.collectTime?.substring(11, 16) || '') },
      yAxis: { type: 'value', name: '水位(cm)' },
      series: [{
        type: 'line',
        data: data.map(d => d.waterLevel),
        smooth: true,
        areaStyle: { opacity: 0.2 },
        itemStyle: { color: '#3ecf8e' }
      }],
      grid: { left: 60, right: 20, bottom: 30, top: 40 }
    })
  } catch (e) {
    // handled
  }
}

const onDeviceChange = () => {
  query.page = 1
  loadData()
  if (showChart.value) loadTrend()
}

const exportExcel = async () => {
  if (!query.deviceId) {
    ElMessage.warning('请先选择设备')
    return
  }
  try {
    const params = { deviceId: query.deviceId, page: 1, size: 5000 }
    if (dateRange.value && dateRange.value.length === 2) {
      params.start = dateRange.value[0]
      params.end = dateRange.value[1]
    }
    const res = await getWaterHistory(params)
    const data = (res.data?.records || []).map(row => ({
      'ID': row.id,
      '设备ID': row.deviceId,
      '水位(cm)': row.waterLevel,
      '电池电压(V)': row.batteryVoltage ?? '',
      '采集时间': row.collectTime || ''
    }))
    if (data.length === 0) {
      ElMessage.warning('没有数据可导出')
      return
    }
    const ws = XLSX.utils.json_to_sheet(data)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '水位数据')
    ws['!cols'] = [{ wch: 8 }, { wch: 12 }, { wch: 12 }, { wch: 14 }, { wch: 20 }]
    XLSX.writeFile(wb, `水位数据_${query.deviceId}_${new Date().toISOString().substring(0, 10)}.xlsx`)
    ElMessage.success(`导出 ${data.length} 条记录`)
  } catch (e) {
    ElMessage.error('导出失败')
    console.error(e)
  }
}

onMounted(() => {
  loadDevices().then(() => {
    loadData()
  })
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
