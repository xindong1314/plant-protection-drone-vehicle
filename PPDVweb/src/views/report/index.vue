<template>
  <div class="report-page">
    <div class="page-heading">
      <div><div class="eyebrow">PERFORMANCE ANALYTICS</div><h1>数据报表</h1><p>分析巡检、异常、点喷任务和区域风险</p></div>
      <span class="live-status">数据统计服务正常</span>
    </div>
    <!-- 1. 时间范围筛选栏 -->
    <div class="filter-bar surface-card">
      <el-date-picker
        v-model="filter.startTime"
        type="datetime"
        placeholder="开始时间"
        format="YYYY-MM-DD HH:mm:ss"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 220px"
        :clearable="false"
      />
      <span class="filter-sep">—</span>
      <el-date-picker
        v-model="filter.endTime"
        type="datetime"
        placeholder="结束时间"
        format="YYYY-MM-DD HH:mm:ss"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 220px"
        :clearable="false"
      />
      <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      <el-button :icon="Download" :disabled="!report || loading" @click="handleExport">导出 CSV</el-button>
      <span v-if="report" class="filter-period">
        当前范围：{{ report.period?.start || '不限' }} — {{ report.period?.end || '不限' }}
      </span>
    </div>

    <!-- 2. 4 个统计卡片行 -->
    <el-row :gutter="16" class="card-row">
      <!-- 卡片① 巡检 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-title">巡检</div>
          <div class="card-stats">
            <div class="stat-item">
              <span class="stat-num">{{ report?.inspections?.total ?? '-' }}</span>
              <span class="stat-label">总数</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.inspections?.completed ?? '-' }}</span>
              <span class="stat-label">已完成</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.inspections?.inProgress ?? '-' }}</span>
              <span class="stat-label">进行中</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 卡片② 异常 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-title">异常</div>
          <div class="card-stats">
            <div class="stat-item">
              <span class="stat-num">{{ report?.anomalies?.total ?? '-' }}</span>
              <span class="stat-label">总数</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 卡片③ 点喷任务 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-title">点喷任务</div>
          <div class="card-stats">
            <div class="stat-item">
              <span class="stat-num">{{ report?.sprayTasks?.total ?? '-' }}</span>
              <span class="stat-label">总数</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.sprayTasks?.completed ?? '-' }}</span>
              <span class="stat-label">已完成</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.sprayTasks?.failed ?? '-' }}</span>
              <span class="stat-label">失败</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.sprayTasks?.pending ?? '-' }}</span>
              <span class="stat-label">待处理</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 卡片④ 复查 -->
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-title">复查</div>
          <div class="card-stats">
            <div class="stat-item">
              <span class="stat-num">{{ report?.reviews?.total ?? '-' }}</span>
              <span class="stat-label">总数</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.reviews?.passed ?? '-' }}</span>
              <span class="stat-label">通过</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.reviews?.failed ?? '-' }}</span>
              <span class="stat-label">失败</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ report?.reviews?.needRetry ?? '-' }}</span>
              <span class="stat-label">需重喷</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 3. 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">异常分类分布</div>
          <div ref="pieChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 4. 区域概览表格 -->
    <el-card shadow="hover" class="table-card">
      <div class="card-title">区域概览</div>
      <el-table v-loading="loading" :data="report?.areas ?? []" style="width: 100%">
        <el-table-column prop="areaCode" label="区域编号" width="120" />
        <el-table-column prop="areaName" label="名称" width="160" />
        <el-table-column prop="riskStatus" label="风险状态" width="120">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskStatus)" size="small">
              {{ row.riskStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalInspections" label="巡检次数" width="100" />
        <el-table-column prop="totalAnomalies" label="异常次数" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getReport } from '@/api/ppdv/report'

const loading = ref(false)
const filter = reactive({
  startTime: null,
  endTime: null
})
const report = ref<any>(null)
const pieChartRef = ref<HTMLDivElement>()
let pieChart: echarts.ECharts | null = null

function riskTagType(status: string) {
  const map: Record<string, string> = {
    NORMAL: 'success',
    SUSPICIOUS: 'warning',
    HIGH_RISK: 'danger'
  }
  return map[status] || 'info'
}

async function fetchReport(startTime?: string, endTime?: string) {
  loading.value = true
  try {
    const res: any = await getReport(startTime, endTime)
    report.value = res.data || res
    await nextTick()
    renderPieChart()
  } catch {
    // request.js 拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

function renderPieChart() {
  if (!pieChartRef.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieChartRef.value)
  }
  const byCategory = report.value?.anomalies?.byCategory || {}
  const pieData = Object.entries(byCategory).map(([name, value]) => ({ name, value }))
  pieChart.setOption({
    textStyle: {
      fontFamily: '"Microsoft YaHei", "PingFang SC", "SimHei", "Noto Sans SC", sans-serif'
    },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data: pieData,
      color: ['#76b900', '#3f493f', '#c77700', '#8ea17e', '#d93025'],
      label: { formatter: '{b}: {c}' },
      emphasis: {
        scaleSize: 10
      }
    }]
  })
}

function handleQuery() {
  const start = filter.startTime || undefined
  const end = filter.endTime || undefined
  fetchReport(start, end)
}

function handleReset() {
  filter.startTime = null
  filter.endTime = null
  fetchReport()
}

function escapeCsv(value: unknown): string {
  const text = value == null ? '' : String(value)
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function formatFileTime(date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

/** 将当前查询结果导出为 UTF-8 BOM CSV，可直接用 Excel 打开。 */
function handleExport() {
  if (!report.value) return

  const data = report.value
  const rows: unknown[][] = [
    ['植保无人车数据报表'],
    ['统计范围', data.period?.start || '不限', data.period?.end || '不限'],
    [],
    ['巡检统计'],
    ['指标', '数值'],
    ['总数', data.inspections?.total ?? 0],
    ['已完成', data.inspections?.completed ?? 0],
    ['进行中', data.inspections?.inProgress ?? 0],
    [],
    ['异常统计'],
    ['指标', '数值'],
    ['总数', data.anomalies?.total ?? 0],
    ...Object.entries(data.anomalies?.byCategory || {}).map(([category, count]) => [`分类：${category}`, count]),
    [],
    ['点喷任务统计'],
    ['指标', '数值'],
    ['总数', data.sprayTasks?.total ?? 0],
    ['已完成', data.sprayTasks?.completed ?? 0],
    ['失败', data.sprayTasks?.failed ?? 0],
    ['待处理', data.sprayTasks?.pending ?? 0],
    [],
    ['复查统计'],
    ['指标', '数值'],
    ['总数', data.reviews?.total ?? 0],
    ['通过', data.reviews?.passed ?? 0],
    ['失败', data.reviews?.failed ?? 0],
    ['需重喷', data.reviews?.needRetry ?? 0],
    [],
    ['区域概览'],
    ['区域编号', '名称', '风险状态', '巡检次数', '异常次数'],
    ...(data.areas || []).map((area: any) => [
      area.areaCode,
      area.areaName,
      area.riskStatus,
      area.totalInspections,
      area.totalAnomalies
    ])
  ]
  const csv = '\uFEFF' + rows.map(row => row.map(escapeCsv).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `植保无人车数据报表-${formatFileTime()}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  fetchReport()
})
</script>

<style scoped>
.report-page {
  padding: 0;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 13px 14px;
}

.filter-sep {
  color: var(--ag-muted);
}

.filter-period {
  font-size: 12px;
  color: var(--ag-muted);
  margin-left: 12px;
}

.card-row {
  margin-bottom: 18px;
}
.card-row :deep(.el-card__body) { min-height: 142px; padding: 20px; }

.card-title {
  font-size: 14px;
  color: var(--ag-ink);
  font-weight: 650;
  margin-bottom: 12px;
}

.card-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 60px;
}

.stat-num {
  font-size: 28px;
  font-weight: 680;
  color: var(--ag-ink);
  letter-spacing: -1px;
}

.stat-label {
  font-size: 12px;
  color: var(--ag-muted);
  margin-top: 4px;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.table-card {
  margin-bottom: 16px;
}
</style>
