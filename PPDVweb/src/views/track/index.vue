<template>
  <div class="track-page">
    <div class="page-heading">
      <div><div class="eyebrow">TARGET TRACKING</div><h1>跟踪记录</h1><p>按会话归集目标出现片段（episode），查看类别、置信度与跟踪状态</p></div>
      <el-button :icon="Refresh" @click="loadSessions">刷新</el-button>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="8"><el-card><div class="stat-num">{{ totalSessions }}</div><div class="stat-label">跟踪会话</div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat-num success">{{ pageTrackTotal }}</div><div class="stat-label">本页出现次数</div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat-num running">{{ pageActiveCount }}</div><div class="stat-label">本页进行中会话</div></el-card></el-col>
    </el-row>

    <section class="surface-card table-shell">
      <div class="toolbar">
        <el-select v-model="filters.areaId" clearable filterable :disabled="!canListAreas" :placeholder="canListAreas ? '全部区域' : '无区域权限'" style="width:170px">
          <el-option v-for="area in areas" :key="area.id" :label="areaLabel(area)" :value="area.id" />
        </el-select>
        <el-date-picker v-model="filters.range" type="daterange" value-format="x" :default-time="defaultRangeTime" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width:250px" />
        <el-input v-model="filters.deviceId" clearable placeholder="设备编号" style="width:150px" @keyup.enter="handleQuery" />
        <el-select v-model="filters.category" clearable placeholder="全部类别" style="width:150px">
          <el-option v-for="item in classes" :key="item.key" :label="item.label" :value="item.key" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="sessions" @row-click="openDetail">
        <el-table-column label="开始时间" min-width="165"><template #default="{ row }">{{ formatTime(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束时间" min-width="165"><template #default="{ row }">{{ formatTime(row.endTime) }}</template></el-table-column>
        <el-table-column prop="deviceId" label="设备" min-width="125" />
        <el-table-column label="区域" min-width="150"><template #default="{ row }">{{ areaName(row.areaId) }}</template></el-table-column>
        <el-table-column label="出现次数" width="90"><template #default="{ row }">{{ row.episodeCount ?? 0 }}</template></el-table-column>
        <el-table-column label="去重目标" width="90"><template #default="{ row }">{{ row.distinctTrackCount ?? 0 }}</template></el-table-column>
        <el-table-column label="类别" min-width="190">
          <template #default="{ row }">
            <span v-if="row.categories?.length" class="category-list"><el-tag v-for="key in row.categories" :key="key" size="small" type="info">{{ categoryLabel(key) }}</el-tag></span>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="最高置信度" width="110"><template #default="{ row }">{{ percent(row.maxConfidence) }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '进行中' : '已结束' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">查看</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="totalSessions"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadSessions"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" :title="`跟踪会话 #${selectedSession?.id || ''}`" size="820px" destroy-on-close>
      <div v-if="selectedSession" v-loading="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="detail-alert" />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="跟踪设备">{{ selectedSession.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="跟踪区域">{{ areaName(selectedSession.areaId) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(selectedSession.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(selectedSession.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="会话标识">{{ selectedSession.sessionKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="会话状态">{{ selectedSession.status === 'ACTIVE' ? '进行中' : '已结束' }}</el-descriptions-item>
          <el-descriptions-item label="出现次数">{{ selectedSession.episodeCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="去重目标">{{ selectedSession.distinctTrackCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="最高置信度">{{ percent(selectedSession.maxConfidence) }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title"><span>出现记录</span><span class="muted">{{ detailEpisodeLabel }}，点击行查看轨迹</span></div>
        <el-table :data="tracks" size="small" highlight-current-row @row-click="selectTrack">
          <el-table-column prop="trackKey" label="目标标识" min-width="100" />
          <el-table-column prop="episodeKey" label="出现标识" min-width="150" show-overflow-tooltip />
          <el-table-column label="次序" width="70"><template #default="{ row }">{{ row.episodeNo ?? 0 }}</template></el-table-column>
          <el-table-column label="类别" width="110"><template #default="{ row }">{{ categoryLabel(row.category) }}</template></el-table-column>
          <el-table-column label="置信度" width="90"><template #default="{ row }">{{ percent(row.confidence) }}</template></el-table-column>
          <el-table-column label="首次 / 末次" min-width="185"><template #default="{ row }">{{ formatTime(row.firstSeenTs) }} / {{ formatTime(row.lastSeenTs) }}</template></el-table-column>
          <el-table-column label="包围框" min-width="140"><template #default="{ row }">{{ bboxLabel(row) }}</template></el-table-column>
          <el-table-column label="状态" width="95"><template #default="{ row }"><el-tag size="small" :type="trackStateMeta(row.trackState).type">{{ trackStateMeta(row.trackState).label }}</el-tag></template></el-table-column>
        </el-table>

        <div class="section-title"><span>轨迹预览</span><span class="muted">{{ previewTrack ? `${previewTrack.episodeKey || previewTrack.trackKey} · ${categoryLabel(previewTrack.category)}` : '请选择出现记录' }}</span></div>
        <canvas v-if="previewTrack" ref="trajectoryCanvas" class="trajectory-canvas" width="560" height="320" />
        <el-empty v-else description="暂无出现记录" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listTrackClasses, getTrackSession, listTrackSessions } from '@/api/track'
import { listAreas } from '@/api/inspection'
import { useUserStore } from '@/store/user'

const TRACK_STATE_MAP = {
  ACTIVE: { label: '跟踪中', type: 'success' },
  LOST: { label: '已丢失', type: 'danger' },
  ENDED: { label: '已结束', type: 'info' },
  REAPPEAR: { label: '重新出现', type: 'warning' }
}
const defaultRangeTime = [new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]

const userStore = useUserStore()
const loading = ref(false), sessions = ref([]), areas = ref([]), classes = ref([])
const pageNum = ref(1), pageSize = ref(20), totalSessions = ref(0)
const filters = ref({ areaId: null, range: null, deviceId: '', category: '' })
const detailVisible = ref(false), detailLoading = ref(false), detailError = ref(''), selectedSession = ref(null)
const tracks = ref([]), previewTrack = ref(null)
const trajectoryCanvas = ref(null)
const canListAreas = computed(() => userStore.hasPermission('ppdv:area:list'))
const categoryLabels = computed(() => classes.value.reduce((map, item) => { map[item.key] = item.label; return map }, {}))
const pageTrackTotal = computed(() => sessions.value.reduce((total, row) => total + Number(row.episodeCount || 0), 0))
const pageActiveCount = computed(() => sessions.value.filter(row => row.status === 'ACTIVE').length)
const detailEpisodeLabel = computed(() => {
  const total = Number(selectedSession.value?.episodeCount ?? tracks.value.length)
  return tracks.value.length < total ? `共 ${total} 次出现，仅展示前 ${tracks.value.length} 条` : `共 ${total} 次出现`
})

let sessionRequestId = 0
let detailRequestId = 0

function responseData(response) { return response.data ?? response }
function areaLabel(area) { return `${area.areaCode || ''} ${area.areaName || '未命名区域'}`.trim() }
function areaName(id) { const area = areas.value.find(item => item.id === id); return area ? areaLabel(area) : (id ? `区域#${id}` : '未指定') }
function categoryLabel(key) { return categoryLabels.value[key] || '未知' }
function trackStateMeta(state) { return TRACK_STATE_MAP[state] || { label: state || '未知', type: 'info' } }
function formatTime(value) { if (!value) return '-'; const date = new Date(value); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false }) }
function percent(value) { return value == null ? '-' : `${Math.round(Number(value) * 100)}%` }
function bboxLabel(row) { return row.boxX == null || row.boxY == null || row.boxW == null || row.boxH == null ? '-' : `(${row.boxX},${row.boxY}) ${row.boxW}×${row.boxH}` }
function parseTrajectory(json) {
  if (!json) return []
  try {
    const parsed = JSON.parse(json)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(point => Array.isArray(point) && Number.isFinite(Number(point[0])) && Number.isFinite(Number(point[1])))
      .map(point => [Number(point[0]), Number(point[1])])
  } catch (error) {
    return []
  }
}

async function loadOptions() {
  const tasks = []
  tasks.push(listTrackClasses().then(result => { classes.value = responseData(result) || [] }).catch(() => { classes.value = [] }))
  if (canListAreas.value) tasks.push(listAreas().then(result => { areas.value = responseData(result) || [] }).catch(() => { areas.value = [] }))
  await Promise.allSettled(tasks)
}

async function loadSessions() {
  const requestId = ++sessionRequestId
  loading.value = true
  try {
    // 后端 PageQuery 统一钳制 pageSize（上限 500），前端不做双重钳制。
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filters.value.areaId != null) params.areaId = filters.value.areaId
    if (filters.value.deviceId.trim()) params.deviceId = filters.value.deviceId.trim()
    if (filters.value.category) params.category = filters.value.category
    if (filters.value.range?.length === 2) {
      params.from = Number(filters.value.range[0])
      params.to = Number(filters.value.range[1])
    }
    const result = await listTrackSessions(params)
    if (requestId !== sessionRequestId) return
    sessions.value = result.rows || []
    totalSessions.value = Number(result.total) || 0
  } catch (error) {
    if (requestId !== sessionRequestId) return
    sessions.value = []
    totalSessions.value = 0
    ElMessage.error(error?.message || '跟踪会话加载失败')
  } finally {
    if (requestId === sessionRequestId) loading.value = false
  }
}
function handleQuery() { pageNum.value = 1; loadSessions() }
function handlePageSizeChange() { pageNum.value = 1; loadSessions() }
function resetFilters() { filters.value = { areaId: null, range: null, deviceId: '', category: '' }; handleQuery() }

async function openDetail(row) {
  const requestId = ++detailRequestId
  selectedSession.value = row
  tracks.value = []
  previewTrack.value = null
  detailError.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const result = await getTrackSession(row.id)
    if (requestId !== detailRequestId) return
    selectedSession.value = result.session || row
    tracks.value = result.tracks || []
    previewTrack.value = tracks.value.find(item => parseTrajectory(item.trajectoryJson).length) || tracks.value[0] || null
  } catch (error) {
    if (requestId === detailRequestId) detailError.value = error?.message || '跟踪会话详情加载失败'
  } finally {
    if (requestId === detailRequestId) {
      detailLoading.value = false
      await nextTick()
      drawTrajectory()
    }
  }
}
async function selectTrack(row) { previewTrack.value = row; await nextTick(); drawTrajectory() }

function drawTrajectory() {
  const canvas = trajectoryCanvas.value
  const track = previewTrack.value
  if (!canvas) return
  const context = canvas.getContext('2d')
  const width = canvas.width, height = canvas.height
  context.clearRect(0, 0, width, height)
  context.fillStyle = '#f8f9f6'
  context.fillRect(0, 0, width, height)
  const points = parseTrajectory(track?.trajectoryJson)
  if (!points.length) {
    context.fillStyle = '#687267'
    context.font = '13px Inter, "PingFang SC", "Microsoft YaHei", Arial, sans-serif'
    context.textAlign = 'center'
    context.fillText('暂无轨迹', width / 2, height / 2)
    return
  }
  const hasImageSize = Number(track.imageWidth) > 0 && Number(track.imageHeight) > 0
  const hasBox = Number(track.boxW) > 0 && Number(track.boxH) > 0 && Number.isFinite(Number(track.boxX)) && Number.isFinite(Number(track.boxY))
  let minX, maxX, minY, maxY
  if (hasImageSize) {
    minX = 0; maxX = Number(track.imageWidth); minY = 0; maxY = Number(track.imageHeight)
  } else if (hasBox) {
    minX = Number(track.boxX); maxX = minX + Number(track.boxW); minY = Number(track.boxY); maxY = minY + Number(track.boxH)
  } else {
    minX = Math.min(...points.map(point => point[0])); maxX = Math.max(...points.map(point => point[0]))
    minY = Math.min(...points.map(point => point[1])); maxY = Math.max(...points.map(point => point[1]))
  }
  const spanX = maxX - minX || 1, spanY = maxY - minY || 1
  const padding = 24
  const scale = Math.min((width - padding * 2) / spanX, (height - padding * 2) / spanY)
  const offsetX = (width - spanX * scale) / 2, offsetY = (height - spanY * scale) / 2
  const toPixelX = value => offsetX + (value - minX) * scale
  const toPixelY = value => offsetY + (value - minY) * scale
  context.strokeStyle = '#b9c1b3'
  context.lineWidth = 1
  context.strokeRect(toPixelX(minX), toPixelY(minY), spanX * scale, spanY * scale)
  context.strokeStyle = '#76b900'
  context.lineWidth = 2
  context.beginPath()
  points.forEach((point, index) => {
    const x = toPixelX(point[0]), y = toPixelY(point[1])
    if (index === 0) context.moveTo(x, y)
    else context.lineTo(x, y)
  })
  context.stroke()
  const start = points[0], end = points[points.length - 1]
  context.fillStyle = '#418600'
  context.beginPath()
  context.arc(toPixelX(start[0]), toPixelY(start[1]), 4, 0, Math.PI * 2)
  context.fill()
  context.fillStyle = '#d93025'
  context.beginPath()
  context.arc(toPixelX(end[0]), toPixelY(end[1]), 4, 0, Math.PI * 2)
  context.fill()
}

onMounted(() => { loadOptions(); loadSessions() })
</script>

<style scoped>
.page-heading { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:18px; }.eyebrow { color:var(--ag-primary); font-size:11px; font-weight:700; letter-spacing:1.5px; }.page-heading h1 { margin:5px 0 6px; font-size:28px; }.page-heading p { margin:0; color:var(--ag-muted); }.stat-row { margin-bottom:18px; }.stat-row :deep(.el-card__body) { padding:19px 22px; }.stat-num { color:var(--ag-ink); font-size:32px; font-weight:700; }.stat-num.running { color:#c77700; }.stat-num.success { color:#76b900; }.stat-label { margin-top:6px; color:var(--ag-muted); font-size:13px; }.table-shell { overflow:hidden; }.toolbar { display:flex; flex-wrap:wrap; gap:10px; padding:14px; }.toolbar .el-input { max-width:280px; }.pagination-bar { display:flex; justify-content:center; padding:14px; }.category-list { display:inline-flex; flex-wrap:wrap; gap:4px; }.muted { color:var(--ag-muted); font-size:12px; }.detail-alert { margin-bottom:16px; }.section-title { display:flex; align-items:center; justify-content:space-between; margin:26px 0 12px; font-size:16px; font-weight:650; }.trajectory-canvas { display:block; width:100%; max-width:560px; height:auto; border:1px solid var(--ag-border); border-radius:4px; }
</style>
