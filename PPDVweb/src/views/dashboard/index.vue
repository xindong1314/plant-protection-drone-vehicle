<template>
  <div class="dashboard-page">
    <div class="page-heading">
      <div><div class="eyebrow">OPERATIONS OVERVIEW</div><h1>监控总览</h1><p>聚合设备状态、实时事件与作业告警</p></div>
      <span class="live-status">实时数据已连接</span>
    </div>
    <!-- 1. 统计卡片行 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-num online">{{ store.onlineCount }}</div>
          <div class="stat-sub">/ {{ store.totalCount }} 总数</div>
          <div class="stat-label">在线设备</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" :class="{ 'alarm-card': store.alarmCount > 0 }">
          <div class="stat-num" :class="{ danger: store.alarmCount > 0 }">{{ store.alarmCount }}</div>
          <div class="stat-label">活跃告警</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-num spraying">{{ store.sprayingCount }}</div>
          <div class="stat-label">正在作业</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-num">{{ todayInspections }}</div>
          <div class="stat-label">今日巡检</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 2. 图表行 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">设备状态分布</div>
          <div ref="statusPieRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">设备电量</div>
          <div v-if="store.totalCount > 0" ref="batteryBarRef" class="chart-container"></div>
          <div v-else class="chart-empty">暂无设备数据</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 3. 底行：事件流 + 告警列表 -->
    <el-row :gutter="16" class="bottom-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">实时事件</div>
          <div class="event-list">
            <div v-if="store.recentEvents.length === 0" class="event-empty">等待遥测数据...</div>
            <div v-for="(ev, i) in store.recentEvents" :key="i" class="event-item">
              <span class="event-time">{{ formatEventTime(ev.ts) }}</span>
              <span class="event-device">{{ ev.deviceId }}</span>
              <span class="event-summary">{{ ev.summary }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">活跃告警</div>
          <el-table v-if="store.recentAlarms.length > 0" :data="store.recentAlarms" style="width: 100%" max-height="280">
            <el-table-column label="时间" width="100">
              <template #default="{ row }">{{ formatEventTime(row.ts) }}</template>
            </el-table-column>
            <el-table-column label="级别" width="80">
              <template #default="{ row }">
                <el-tag :type="alarmLevelTag(row.level)" size="small">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deviceId" label="设备" width="110" />
            <el-table-column prop="msg" label="内容" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="暂无告警" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { useTelemetryStore } from '@/stores/telemetry'
import { listDevice } from '@/api/ppdv/device'
import { getReport } from '@/api/ppdv/report'

const store = useTelemetryStore()
const todayInspections = ref(0)

// ---------- 图表 ----------
const statusPieRef = ref<HTMLDivElement>()
const batteryBarRef = ref<HTMLDivElement>()
let statusPieChart: echarts.ECharts | null = null
let batteryBarChart: echarts.ECharts | null = null

function renderStatusPie() {
  if (!statusPieRef.value) return
  if (!statusPieChart) statusPieChart = echarts.init(statusPieRef.value)
  const offline = store.totalCount - store.onlineCount
  statusPieChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['50%', '70%'],
      center: ['50%', '50%'],
      label: { show: false },
      emphasis: { scaleSize: 8 },
      data: [
        { value: store.onlineCount, name: '在线', itemStyle: { color: '#76b900' } },
        { value: Math.max(offline, 0), name: '离线', itemStyle: { color: '#d9ded4' } },
      ]
    }]
  })
}

function renderBatteryBar() {
  if (!batteryBarRef.value || store.totalCount === 0) return
  if (!batteryBarChart) batteryBarChart = echarts.init(batteryBarRef.value)
  const list = store.deviceList
  batteryBarChart.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
    grid: { left: 40, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'category', data: list.map(d => d.deviceId || '?') },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'bar',
      data: list.map(d => {
        const v = d.battery ?? 0
        let color = '#d93025'
        if (v >= 60) color = '#76b900'
        else if (v >= 20) color = '#c77700'
        return { value: v, itemStyle: { color } }
      }),
    }]
  })
}

function renderAllCharts() {
  renderStatusPie()
  renderBatteryBar()
}

// ---------- 格式化 ----------
function pad(n: number): string { return String(n).padStart(2, '0') }
function formatEventTime(ts: number): string {
  const d = new Date(ts)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
function alarmLevelTag(level: string): string {
  const map: Record<string, string> = { CRITICAL: 'danger', WARN: 'warning', INFO: '' }
  return map[level] || 'info'
}

// ---------- 初始化 ----------
async function init() {
  try {
    // 1. 拉设备列表
    const deviceRes: any = await listDevice()
    store.initDevices(deviceRes.rows || [])
    renderAllCharts()
  } catch {
    // request.js 拦截器已反馈设备接口错误
  }

  try {
    // 2. 今日报表独立加载，避免报表权限影响设备图表
    const today = new Date()
    const startStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())} 00:00:00`
    const reportRes: any = await getReport(startStr)
    todayInspections.value = reportRes.data?.inspections?.total ?? reportRes.inspections?.total ?? 0
  } catch {
    // request.js 拦截器已反馈报表接口错误
  }

  // 3. WebSocket
  store.connect()
}

// ---------- 30 秒刷新图表 ----------
let chartTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  init()
  chartTimer = setInterval(renderAllCharts, 30000)
})

onUnmounted(() => {
  store.disconnect()
  if (chartTimer) {
    clearInterval(chartTimer)
  }
  statusPieChart?.dispose()
  batteryBarChart?.dispose()
})
</script>

<style scoped>
.dashboard-page { padding: 0; }
.stat-row { margin-bottom: 18px; }
.stat-row :deep(.el-card__body) { min-height: 122px; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; padding: 20px; }
.stat-num {
  font-size: 38px; font-weight: 680; color: var(--ag-ink); letter-spacing: -1.5px;
}
.stat-num.online, .stat-num.spraying { color: var(--ag-success); }
.stat-num.danger { color: var(--ag-danger); }
.stat-sub {
  font-size: 11px; color: var(--ag-muted); margin-top: -2px;
}
.stat-label {
  font-size: 12px; color: var(--ag-muted); margin-top: 9px;
}
.alarm-card { border-left: 3px solid var(--ag-danger) !important; }
.card-title { font-size: 14px; color: var(--ag-ink); font-weight: 650; margin-bottom: 12px; }
.chart-row { margin-bottom: 18px; }
.chart-container { width: 100%; height: 280px; }
.chart-empty { text-align: center; color: var(--ag-muted); padding: 80px 0; font-size: 13px; }
.bottom-row { margin-bottom: 16px; }

.event-list { max-height: 280px; overflow-y: auto; }
.event-empty { text-align: center; color: var(--ag-muted); padding: 60px 0; font-size: 13px; }
.event-item {
  display: flex; gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--ag-border);
  font-size: 13px; align-items: baseline;
}
.event-item:last-child { border-bottom: none; }
.event-time { color: #9aa299; white-space: nowrap; font-family: monospace; }
.event-device { color: var(--ag-success); font-family: monospace; white-space: nowrap; font-weight: 600; }
.event-summary { color: var(--ag-ink); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
