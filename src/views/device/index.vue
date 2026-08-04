<template>
  <!-- 统计卡片 -->
  <el-row :gutter="16" class="stat-row">
    <el-col :span="6">
      <el-card shadow="hover">
        <div class="stat-num">{{ allData.length }}</div>
        <div class="stat-label">设备总数</div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover">
        <div class="stat-num online">{{ onlineCount }}</div>
        <div class="stat-label">在线</div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover">
        <div class="stat-num offline">{{ offlineCount }}</div>
        <div class="stat-label">离线</div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover">
        <div class="stat-num">{{ avgBattery }}</div>
        <div class="stat-label">平均电量</div>
      </el-card>
    </el-col>
  </el-row>

  <!-- 搜索表单 -->
  <div class="search-bar">
    <el-input v-model="searchKeyword" style="width: 240px" placeholder="搜索设备编号/名称/状态/区域" />
    <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
    <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    <span class="refresh-time">上次刷新: {{ lastRefreshTime }}</span>
  </div>

  <!-- 数据表格 -->
  <el-table v-loading="loading" :data="pagedData" style="width: 100%" class="device-table" @row-click="handleRowClick">
    <el-table-column prop="deviceId" label="设备编号" width="120" />
    <el-table-column prop="name" label="名称" width="140" />
    <el-table-column prop="model" label="型号" width="120" show-overflow-tooltip />
    <el-table-column prop="status" label="在线状态" width="100">
      <template #default="{ row }">
        <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small">
          {{ row.status === 'ONLINE' ? '在线' : '离线' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="battery" label="电量" width="180">
      <template #default="{ row }">
        <template v-if="row.battery != null">
          <el-progress
            :percentage="row.battery"
            :stroke-width="16"
            :color="batteryColor(row.battery)"
          />
        </template>
        <span v-else class="text-muted">未上报</span>
      </template>
    </el-table-column>
    <el-table-column prop="mode" label="工作模式" width="100">
      <template #default="{ row }">
        <el-tag v-if="row.mode" :type="modeTagType(row.mode)" size="small">
          {{ modeLabel(row.mode) }}
        </el-tag>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column prop="currentZone" label="所在区域" width="110">
      <template #default="{ row }">
        {{ row.currentZone || '-' }}
      </template>
    </el-table-column>
    <el-table-column prop="lastSeenAt" label="最后在线" width="180">
      <template #default="{ row }">
        <span v-if="row.lastSeenAt">{{ formatTime(row.lastSeenAt) }}</span>
        <span v-else class="text-muted">从未上线</span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="100">
      <template #default="{ row }">
        <el-button type="primary" link size="small" @click.stop="handleRowClick(row)">查看详情</el-button>
      </template>
    </el-table-column>
  </el-table>

  <!-- 分页器 -->
  <div class="pagination-bar">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      background
    />
  </div>

  <!-- 设备详情弹窗 -->
  <el-dialog
    v-model="dialogVisible"
    :title="'设备详情 - ' + currentDevice?.deviceId"
    width="700px"
    @close="handleDialogClose"
  >
    <div v-loading="detailLoading">
      <!-- 基本信息 -->
      <el-descriptions v-if="currentDevice" :column="2" border>
        <el-descriptions-item label="设备编号">{{ currentDevice.deviceId }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ currentDevice.name }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ currentDevice.model }}</el-descriptions-item>
        <el-descriptions-item label="固件版本">{{ currentDevice.firmware || '-' }}</el-descriptions-item>
        <el-descriptions-item label="在线状态">
          <el-tag :type="currentDevice.status === 'ONLINE' ? 'success' : 'info'" size="small">
            {{ currentDevice.status === 'ONLINE' ? '在线' : '离线' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工作模式">
          <el-tag v-if="currentDevice.mode" :type="modeTagType(currentDevice.mode)" size="small">
            {{ modeLabel(currentDevice.mode) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="所在区域">{{ currentDevice.currentZone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近坐标">
          <template v-if="currentDevice.lastLat != null && currentDevice.lastLon != null">
            {{ currentDevice.lastLat.toFixed(6) }}, {{ currentDevice.lastLon.toFixed(6) }}
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="最后在线">
          {{ currentDevice.lastSeenAt ? formatTime(currentDevice.lastSeenAt) : '从未上线' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 电量趋势图 -->
      <div class="chart-section">
        <div class="chart-title">电量趋势（最近 {{ telemetryLimit }} 条）</div>
        <div v-if="telemetryData.length > 0" ref="batteryChartRef" class="chart-container"></div>
        <div v-else class="chart-empty">暂无遥测数据</div>
      </div>

      <!-- 最近坐标轨迹 -->
      <div v-if="recentTrack.length > 0" class="track-info">
        最近位置: {{ recentTrack.join(' → ') }}
      </div>
    </div>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listDevice, getStateHistory } from '@/api/ppdv/device'
import * as echarts from 'echarts'

// ---------- 类型 ----------
interface DeviceInfo {
  id: number
  deviceId: string
  name: string
  model: string
  status: string
  battery: number | null
  mode: string | null
  currentZone: string | null
  lastSeenAt: string | null
  firmware: string | null
  lastLat: number | null
  lastLon: number | null
}

// ---------- 数据 ----------
const loading = ref(false)
const searchKeyword = ref('')
const allData = ref<DeviceInfo[]>([])
const tableData = ref<DeviceInfo[]>([])
const lastRefreshTime = ref('--:--:--')

async function fetchList() {
  loading.value = true
  try {
    const res: any = await listDevice()
    allData.value = (res.rows || []) as DeviceInfo[]
    tableData.value = [...allData.value]
    total.value = res.total
    currentPage.value = 1
    lastRefreshTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch {
    // request.js 拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

// ---------- 统计卡片 ----------
const onlineCount = computed(() => allData.value.filter(d => d.status === 'ONLINE').length)
const offlineCount = computed(() => allData.value.length - onlineCount.value)
const avgBattery = computed(() => {
  const batteries = allData.value
    .map(d => d.battery)
    .filter((b): b is number => b != null)
  if (batteries.length === 0) return '-'
  return Math.round(batteries.reduce((a, b) => a + b, 0) / batteries.length) + '%'
})

// ---------- 搜索 ----------
function handleSearch() {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) {
    tableData.value = [...allData.value]
    total.value = allData.value.length
  } else {
    const filtered = allData.value.filter(
      d =>
        d.deviceId.toLowerCase().includes(kw) ||
        d.name.toLowerCase().includes(kw) ||
        d.status.toLowerCase().includes(kw) ||
        (d.currentZone && d.currentZone.toLowerCase().includes(kw))
    )
    tableData.value = filtered
    total.value = filtered.length
  }
  currentPage.value = 1
}

function handleReset() {
  searchKeyword.value = ''
  tableData.value = [...allData.value]
  total.value = allData.value.length
  currentPage.value = 1
}

// ---------- 分页 ----------
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return tableData.value.slice(start, start + pageSize.value)
})

// ---------- 渲染辅助 ----------
function batteryColor(percentage: number): string {
  if (percentage >= 60) return '#67C23A'
  if (percentage >= 20) return '#E6A23C'
  return '#F56C6C'
}

function modeTagType(mode: string): string {
  const map: Record<string, string> = { AUTO: '', MANUAL: 'warning', PAUSED: 'info', ESTOP: 'danger' }
  return map[mode] || 'info'
}

function modeLabel(mode: string): string {
  const map: Record<string, string> = { AUTO: '自动', MANUAL: '手动', PAUSED: '暂停', ESTOP: '急停' }
  return map[mode] || mode
}

function formatTime(isoStr: string): string {
  const d = new Date(isoStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---------- 详情弹窗 ----------
const dialogVisible = ref(false)
const detailLoading = ref(false)
const currentDevice = ref<DeviceInfo | null>(null)
const telemetryData = ref<any[]>([])
const telemetryLimit = 20
const batteryChartRef = ref<HTMLDivElement>()
let batteryChart: echarts.ECharts | null = null

const recentTrack = computed(() => {
  return telemetryData.value
    .filter((d: any) => d.lat != null && d.lon != null)
    .slice(0, 3)
    .map((d: any) => `${d.lat.toFixed(4)},${d.lon.toFixed(4)}`)
})

async function handleRowClick(row: DeviceInfo) {
  currentDevice.value = row
  dialogVisible.value = true
  detailLoading.value = true
  telemetryData.value = []
  try {
    const res: any = await getStateHistory(row.deviceId, telemetryLimit)
    telemetryData.value = res.data || []
    await nextTick()
    renderBatteryChart()
  } catch {
    // request.js 拦截器已弹错误提示
  } finally {
    detailLoading.value = false
  }
}

function handleDialogClose() {
  currentDevice.value = null
  telemetryData.value = []
  if (batteryChart) {
    batteryChart.dispose()
    batteryChart = null
  }
}

function renderBatteryChart() {
  if (!batteryChartRef.value || telemetryData.value.length === 0) return
  if (!batteryChart) {
    batteryChart = echarts.init(batteryChartRef.value)
  }
  // 反转让最近的时间在右边
  const reversed = [...telemetryData.value].reverse()
  batteryChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: reversed.map((d: any) => new Date(d.ts).toLocaleTimeString('zh-CN', { hour12: false }))
    },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'line',
      data: reversed.map((d: any) => d.battery),
      smooth: true,
      lineStyle: { color: '#409EFF' },
      itemStyle: { color: '#409EFF' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(64,158,255,0.3)' },
        { offset: 1, color: 'rgba(64,158,255,0.05)' }
      ])}
    }]
  })
}

// ---------- 自动轮询 ----------
let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  fetchList()
  refreshTimer = setInterval(() => {
    fetchList()
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-num {
  font-size: 36px;
  font-weight: bold;
  color: #303133;
  text-align: center;
}

.stat-num.online {
  color: #67C23A;
}

.stat-num.offline {
  color: #909399;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  text-align: center;
  margin-top: 4px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.refresh-time {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.device-table {
  margin-top: 16px;
}

:deep(.el-table__row) {
  cursor: pointer;
}

.text-muted {
  color: #909399;
  font-size: 13px;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.chart-section {
  margin-top: 20px;
}

.chart-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.chart-container {
  width: 100%;
  height: 250px;
}

.chart-empty {
  text-align: center;
  color: #909399;
  padding: 60px 0;
  font-size: 14px;
}

.track-info {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
