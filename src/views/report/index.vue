<template>
  <div class="report-page">
    <!-- 1. 时间范围筛选栏 -->
    <div class="filter-bar">
      <el-date-picker
        v-model="filter.startTime"
        type="datetime"
        placeholder="开始时间"
        format="yyyy-MM-dd HH:mm:ss"
        value-format="yyyy-MM-dd HH:mm:ss"
        style="width: 200px"
      />
      <span class="filter-sep">—</span>
      <el-date-picker
        v-model="filter.endTime"
        type="datetime"
        placeholder="结束时间"
        format="yyyy-MM-dd HH:mm:ss"
        value-format="yyyy-MM-dd HH:mm:ss"
        style="width: 200px"
      />
      <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
      <el-button :icon="Refresh" @click="handleReset">重置</el-button>
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
import { Search, Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getReport } from '@/api/ppdv/report'

const loading = ref(false)
const filter = reactive({
  startTime: '',
  endTime: ''
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
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data: pieData,
      label: { formatter: '{b}: {c}' }
    }]
  })
}

function handleQuery() {
  const start = filter.startTime || undefined
  const end = filter.endTime || undefined
  fetchReport(start, end)
}

function handleReset() {
  filter.startTime = ''
  filter.endTime = ''
  fetchReport()
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
}

.filter-sep {
  color: #909399;
}

.filter-period {
  font-size: 12px;
  color: #909399;
  margin-left: 12px;
}

.card-row {
  margin-bottom: 16px;
}

.card-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 12px;
}

.card-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.stat-num {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
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
