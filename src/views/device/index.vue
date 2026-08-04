<template>
  <!-- 搜索表单 -->
  <div class="search-bar">
    <el-input v-model="searchKeyword" style="width: 240px" placeholder="搜索设备编号/名称/状态/区域" />
    <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
    <el-button :icon="Refresh" @click="handleReset">重置</el-button>
    <span class="auto-refresh-hint">每 30 秒自动刷新</span>
  </div>

  <!-- 数据表格 -->
  <el-table v-loading="loading" :data="pagedData" style="width: 100%" class="device-table">
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
</template>

<script lang="ts" setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listDevice } from '@/api/ppdv/device'

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
}

// ---------- 数据 ----------
const loading = ref(false)
const searchKeyword = ref('')
const allData = ref<DeviceInfo[]>([])
const tableData = ref<DeviceInfo[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res: any = await listDevice()
    allData.value = (res.rows || []) as DeviceInfo[]
    tableData.value = [...allData.value]
    total.value = res.total
    currentPage.value = 1
  } catch {
    // request.js 拦截器已弹错误提示
  } finally {
    loading.value = false
  }
}

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
  const map: Record<string, string> = {
    AUTO: '',
    MANUAL: 'warning',
    PAUSED: 'info',
    ESTOP: 'danger'
  }
  return map[mode] || 'info'
}

function modeLabel(mode: string): string {
  const map: Record<string, string> = {
    AUTO: '自动',
    MANUAL: '手动',
    PAUSED: '暂停',
    ESTOP: '急停'
  }
  return map[mode] || mode
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
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.auto-refresh-hint {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.device-table {
  margin-top: 16px;
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
</style>
