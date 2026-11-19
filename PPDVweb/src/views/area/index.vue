<template>
  <div class="area-page">
    <div class="page-heading">
      <div><div class="eyebrow">FIELD GIS</div><h1>区域管理</h1><p>维护作业地块边界、作业路线与风险状态</p></div>
      <!-- 边界可空：地图未就绪（如缺 Key）也应能新建无边界的区域；"在地图上圈地块"入口仍依赖地图就绪 -->
      <el-button v-if="canAdd" type="primary" :icon="Plus" @click="beginCreate">新增区域</el-button>
    </div>

    <div class="area-workspace">
      <el-card class="area-list" shadow="never">
        <template #header>
          <div class="list-header"><span>已建区域</span><el-button v-if="canList" link :icon="Refresh" @click="refreshAll">刷新</el-button></div>
        </template>
        <el-input v-model="keyword" placeholder="搜索区域编号/名称" clearable :prefix-icon="Search" class="search-input" />
        <el-table v-loading="loading" :data="filteredAreas" height="520" highlight-current-row @current-change="focusArea">
          <el-table-column prop="areaCode" label="编号" width="88" />
          <el-table-column prop="areaName" label="区域名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="风险" width="76">
            <template #default="{ row }"><el-tag size="small" :type="riskType(row.riskStatus)">{{ riskLabel(row.riskStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="路线" width="76">
            <template #default="{ row }"><el-tag size="small" :type="routeStatus(row).type">{{ routeStatus(row).label }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canEdit || canRemove" label="操作" width="180">
            <template #default="{ row }"><el-button v-if="canEdit" link type="primary" size="small" @click.stop="beginEdit(row)">编辑</el-button><el-button v-if="canEdit" link type="warning" size="small" @click.stop="beginRouteRecording(row)">录入路线</el-button><el-button v-if="canRemove" link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <section class="map-shell">
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-mask">{{ mapError }}</div>
        <div v-else-if="drawingBoundary" class="draw-guide">
          <div><strong>正在圈定地块</strong><span>单击添加顶点，双击完成绘制</span></div>
          <el-button size="small" @click.stop="cancelBoundaryDrawing">取消绘制</el-button>
        </div>
        <div v-else class="map-hint">点击列表可定位地块与路线；新增或编辑时可在地图上圈定边界。</div>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑区域' : '新增区域'" width="560px" @closed="handleDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="区域编号" prop="areaCode"><el-input v-model="form.areaCode" maxlength="16" /></el-form-item>
        <el-form-item label="区域名称" prop="areaName"><el-input v-model="form.areaName" maxlength="64" /></el-form-item>
        <el-form-item label="所属大棚"><el-input v-model="form.greenhouseName" maxlength="64" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="地块边界" prop="boundary">
          <div class="boundary-row">
            <span>{{ boundaryHint }}</span>
            <span class="boundary-actions">
              <!-- 评审修复#9：非 CGCS2000 区域的边界不直接显示（parseAreaBoundary 返回空），
                   但必须能看到“清除边界”入口，否则此类区域编辑被卡死 -->
              <el-button
                v-if="form.id && (form.boundary.length || (form.originalCoordinateSystem && form.originalCoordinateSystem !== 'CGCS2000'))"
                size="small"
                @click="clearBoundaryDraft"
              >清除边界</el-button>
              <el-button type="primary" plain size="small" :disabled="!mapReady" @click="drawBoundary">在地图上圈地块</el-button>
            </span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="form.id && canRemove" type="danger" plain @click="handleDelete(form)">删除</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="canSave" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="recordingDialogVisible"
      title="录入作业路线"
      width="480px"
      :close-on-click-modal="false"
      :before-close="handleRecordingDialogBeforeClose"
      @closed="handleRecordingDialogClosed"
    >
      <el-form label-width="92px">
        <el-form-item label="区域"><span>{{ recording.areaCode }} {{ recording.areaName }}</span></el-form-item>
        <el-form-item v-if="recording.phase === 'select'" label="在线设备">
          <el-select v-model="recording.deviceId" placeholder="选择在线设备" :loading="devicesLoading" :disabled="starting" style="width: 100%">
            <el-option v-for="device in recordingDevices" :key="device.deviceId" :label="`${device.deviceId} ${device.name || ''}`" :value="device.deviceId" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="录制设备"><span>{{ recording.deviceId }}</span></el-form-item>
        <el-form-item v-if="recording.phase === 'recording'" label="录制进度">
          <div class="record-status">
            <el-tag size="small" type="warning">录制中</el-tag>
            <el-tag v-if="recording.baselineCount > 0" size="small" type="info">接管前已录 {{ recording.baselineCount }} 点，折线自接管起显示</el-tag>
            <el-tag v-if="recordingAtLimit" size="small" type="danger">已达上限，后续定位点将被忽略</el-tag>
            <span>已录 {{ recording.baselineCount + recordedPoints.length }} 点<template v-if="lastFixText"> / 最后定位 {{ lastFixText }}</template></span>
          </div>
        </el-form-item>
      </el-form>
      <div class="record-tip">路线由后端随车辆遥测自动记录；开始后驾驶车辆沿作业路线行驶，结束后保存到该区域，一个区域保留一条路线。</div>
      <template #footer>
        <el-button v-if="recording.phase === 'select'" type="primary" :disabled="!recording.deviceId" :loading="starting" @click="startRecording">开始录制</el-button>
        <el-button v-if="recording.phase === 'recording'" type="primary" :loading="stopping" @click="finishRecording">结束并保存</el-button>
        <el-button :disabled="starting || stopping" @click="cancelRecording">取消录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { addArea, cancelRouteRecording, deleteArea, listArea, listAreaRoutes, listRouteRecordings, startRouteRecording, stopRouteRecording, updateArea } from '@/api/area'
import { listDevice } from '@/api/device'
import { parseAreaBoundary, parseRouteJson, pathToPoints } from '@/utils/geo'
import { connectTelemetry } from '@/utils/ws'
import { loadTianditu } from '@/utils/tianditu'
import { useUserStore } from '@/store/user'

const areas = ref([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const drawingBoundary = ref(false)
const startDrawingAfterClose = ref(false)
const formRef = ref()
const mapEl = ref()
const mapReady = ref(false)
const mapError = ref('')
const recordings = ref([])
const recordingDialogVisible = ref(false)
const recordingDevices = ref([])
const devicesLoading = ref(false)
const starting = ref(false)
const stopping = ref(false)
const recordedPoints = ref([])
const lastFixAt = ref(null)
// v1.1 评审 M1：列表摘要不再携带 routeJson，完整路线按 id 拉取后缓存渲染
const routeCache = ref({})
// v1.1 评审 S1：编辑旧坐标系区域时必须重画边界才允许保存
const boundaryRedrawn = ref(false)
// v1.1 评审 L7：编辑时显式清除边界的标记（提交 boundaryJson: ''）
const boundaryCleared = ref(false)
const recording = reactive({ areaId: null, areaCode: '', areaName: '', deviceId: '', phase: 'select', baselineCount: 0, originalRoutePointCount: 0 })
// 评审修复#6：请求序号——弹窗关闭或新一轮请求后，旧请求的 await 续体全部作废
let recordingSeq = 0
const userStore = useUserStore()
const form = reactive({ id: null, areaCode: '', areaName: '', greenhouseName: '', arucoId: null, remark: '', boundary: [], coordinateSystem: 'CGCS2000', originalCoordinateSystem: null })
const rules = {
  areaCode: [{ required: true, message: '请输入区域编号', trigger: 'blur' }],
  areaName: [{ required: true, message: '请输入区域名称', trigger: 'blur' }],
  boundary: [{ validator: (_, value, callback) => !value.length || value.length >= 3 ? callback() : callback(new Error('圈定顶点不足 3 个，请重新圈定或留空')), trigger: 'change' }]
}

let TRef = null
let map = null
let polygonTool = null
let draftPolygon = null
let polygons = []
let routeLines = []
let recordingPolyline = null
let wsConn = null

const canList = computed(() => userStore.hasPermission('ppdv:area:list'))
const canAdd = computed(() => userStore.hasPermission('ppdv:area:add'))
const canEdit = computed(() => userStore.hasPermission('ppdv:area:edit'))
const canRemove = computed(() => userStore.hasPermission('ppdv:area:remove'))
const canSave = computed(() => form.id ? canEdit.value : canAdd.value)
const recordingAreaIds = computed(() => new Set(recordings.value.map(item => item.areaId)))
const lastFixText = computed(() => lastFixAt.value ? new Date(lastFixAt.value).toLocaleTimeString('zh-CN', { hour12: false }) : '')
const recordingAtLimit = computed(() => recording.baselineCount + recordedPoints.value.length >= 20000)
const boundaryHint = computed(() => {
  if (form.boundary.length) return `已圈定 ${form.boundary.length} 个顶点`
  if (boundaryCleared.value) return '已清除边界（可重新圈定）'
  // 评审修复#9：旧坐标系边界不进入 form.boundary，需要单独提示可用入口
  if (form.id && form.originalCoordinateSystem && form.originalCoordinateSystem !== 'CGCS2000') {
    return '旧坐标系边界不直接显示，可清除边界或重新圈定'
  }
  return '尚未圈定地块（可选）'
})

const filteredAreas = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return text ? areas.value.filter(area => `${area.areaCode} ${area.areaName}`.toLowerCase().includes(text)) : areas.value
})

function riskType(status) { return { NORMAL: 'success', SUSPICIOUS: 'warning', HIGH_RISK: 'danger', SPRAYED_PENDING_REVIEW: 'info' }[status] || 'info' }
function riskLabel(status) { return { NORMAL: '正常', SUSPICIOUS: '关注', HIGH_RISK: '高风险', SPRAYED_PENDING_REVIEW: '待复查' }[status] || status || '未知' }
function routeStatus(area) {
  if (recordingAreaIds.value.has(area.id)) return { label: '录制中', type: 'warning' }
  return area.hasRoute ? { label: '已录入', type: 'success' } : { label: '未录入', type: 'info' }
}
function toLngLats(boundary) { return boundary.map(point => new TRef.LngLat(point[0], point[1])) }
function polygonOptions(area) {
  const colors = { NORMAL: '#76b900', SUSPICIOUS: '#c77700', HIGH_RISK: '#d93025' }
  const color = colors[area.riskStatus] || '#76b900'
  return { color, weight: 3, opacity: 0.9, fillColor: color, fillOpacity: 0.2 }
}
function renderPolygons() {
  if (!map || !TRef) return
  polygons.forEach(({ overlay }) => map.removeOverLay(overlay))
  polygons = areas.value
    .map(area => ({ area, boundary: parseAreaBoundary(area) }))
    .filter(item => item.boundary.length >= 3)
    .map(item => {
      const overlay = new TRef.Polygon(toLngLats(item.boundary), polygonOptions(item.area))
      overlay.addEventListener('click', () => canEdit.value ? beginEdit(item.area) : focusArea(item.area))
      return { overlay, area: item.area, boundary: item.boundary }
    })
  polygons.forEach(({ overlay }) => map.addOverLay(overlay))
}
function renderRouteLines() {
  if (!map || !TRef) return
  routeLines.forEach(({ overlay }) => map.removeOverLay(overlay))
  routeLines = Object.entries(routeCache.value)
    .map(([areaId, route]) => ({ area: areas.value.find(area => area.id === Number(areaId)), route }))
    .filter(item => item.area && item.route.length >= 2)
    .map(item => ({ overlay: new TRef.Polyline(toLngLats(item.route), { color: '#76b900', weight: 4, opacity: 0.9 }), area: item.area, route: item.route }))
  routeLines.forEach(({ overlay }) => map.addOverLay(overlay))
}
function setDraft(boundary) {
  if (!map || !TRef) return
  if (draftPolygon) map.removeOverLay(draftPolygon)
  draftPolygon = new TRef.Polygon(toLngLats(boundary), { color: '#409eff', weight: 4, opacity: 0.9, fillColor: '#409eff', fillOpacity: 0.2 })
  map.addOverLay(draftPolygon)
  form.boundary = boundary
  boundaryCleared.value = false
  formRef.value?.validateField('boundary').catch(() => {})
  map.setViewport(toLngLats(boundary))
}
function clearDraft() {
  if (draftPolygon && map) map.removeOverLay(draftPolygon)
  draftPolygon = null
}

async function fetchAreas() {
  if (!canList.value) {
    areas.value = []
    routeCache.value = {}
    renderPolygons()
    renderRouteLines()
    return
  }
  loading.value = true
  try {
    const res = await listArea()
    areas.value = res.data || []
    renderPolygons()
    // 评审修复#11：一次批量拉取全部已录路线（GET /ppdv/area/routes），替代逐区域的并行 N+1 请求
    try {
      const routesRes = await listAreaRoutes()
      const entries = (routesRes.data || []).map(item => [item.areaId, parseRouteJson(item.routeJson)])
      routeCache.value = Object.fromEntries(entries.filter(([, route]) => route.length >= 2))
    } catch (_) {
      routeCache.value = {}
    }
    renderRouteLines()
  } catch (_) {
    // 列表失败不影响地图初始化；错误由请求拦截器提示
  } finally { loading.value = false }
}
async function refreshRouteRecordings() {
  if (!canList.value) {
    recordings.value = []
    return
  }
  try {
    const res = await listRouteRecordings()
    recordings.value = res.data || []
  } catch (_) {
    // v1.1 评审 M3：查询失败保留上次状态，避免“录制中”标签被清空；错误由请求拦截器提示
  }
}
async function refreshAll() {
  // v1.1 评审 M3：刷新时同步刷新录制状态，避免“录入路线”入口用旧数据
  await Promise.all([fetchAreas(), refreshRouteRecordings()])
}
async function initMap() {
  try {
    TRef = await loadTianditu()
    map = new TRef.Map(mapEl.value)
    map.centerAndZoom(new TRef.LngLat(120.65, 30.12), 16)
    map.addControl(new TRef.Control.MapType())
    if (window.TMAP_SATELLITE_MAP) map.setMapType(window.TMAP_SATELLITE_MAP)
    polygonTool = new TRef.PolygonTool(map, { color: '#409eff', fillColor: '#409eff', fillOpacity: 0.2 })
    polygonTool.addEventListener('draw', event => {
      const lnglats = event.currentLnglats || event.currentPolygon?.getLngLats?.() || []
      const boundary = pathToPoints(lnglats, { closed: true })
      if (event.currentPolygon && map) map.removeOverLay(event.currentPolygon)
      polygonTool.close()
      if (!drawingBoundary.value) return
      drawingBoundary.value = false
      if (boundary.length >= 3) {
        // v1.1 评审 S1：只有真正重画边界才允许旧坐标系区域改标 CGCS2000
        boundaryRedrawn.value = true
        setDraft(boundary)
      } else {
        ElMessage.warning('圈定顶点不足 3 个，未保存，请重新圈定')
      }
      dialogVisible.value = true
    })
    mapReady.value = true
    renderPolygons()
    renderRouteLines()
  } catch (error) { mapError.value = `${error.message}（填好 Key 后可使用 GIS 地图）` }
}
function resetForm() {
  Object.assign(form, { id: null, areaCode: '', areaName: '', greenhouseName: '', arucoId: null, remark: '', boundary: [], coordinateSystem: 'CGCS2000', originalCoordinateSystem: null })
  boundaryRedrawn.value = false
  boundaryCleared.value = false
  clearDraft()
}
function beginCreate() {
  if (!canAdd.value) return
  resetForm()
  dialogVisible.value = true
}
function beginEdit(area) {
  if (!canEdit.value) return
  const boundary = parseAreaBoundary(area)
  const legacyCoordinateSystem = area.coordinateSystem && area.coordinateSystem !== 'CGCS2000'
  if (area.boundaryJson && legacyCoordinateSystem) {
    ElMessage.warning('该区域边界使用旧坐标系，必须重新圈定边界后才能保存')
  }
  Object.assign(form, {
    id: area.id,
    areaCode: area.areaCode,
    areaName: area.areaName,
    greenhouseName: area.greenhouseName || '',
    arucoId: area.arucoId ?? null,
    remark: area.remark || '',
    boundary,
    coordinateSystem: 'CGCS2000',
    // v1.1 评审 S1：记录数据库中的坐标系，未重画边界时禁止把旧坐标系改标为 CGCS2000
    originalCoordinateSystem: area.coordinateSystem || 'CGCS2000'
  })
  boundaryRedrawn.value = false
  boundaryCleared.value = false
  dialogVisible.value = true
  if (boundary.length) setDraft(boundary)
}
function focusArea(area) {
  if (!map || !TRef || !area?.id) return
  const item = polygons.find(entry => entry.area.id === area.id)
  const routeItem = routeLines.find(entry => entry.area.id === area.id)
  // v1.1 评审 M2：无边界“仅路线”区域回退用路线定位
  if (!item && !routeItem) return
  const points = item
    ? (routeItem ? [...item.boundary, ...routeItem.route] : item.boundary)
    : routeItem.route
  map.setViewport(toLngLats(points))
}
function clearBoundaryDraft() {
  // v1.1 评审 L7：清除边界，提交时 boundaryJson 传空字符串
  form.boundary = []
  boundaryCleared.value = true
  boundaryRedrawn.value = false
  clearDraft()
}
function drawBoundary() {
  if (!mapReady.value || !polygonTool) return
  startDrawingAfterClose.value = true
  dialogVisible.value = false
}
function handleDialogClosed() {
  if (startDrawingAfterClose.value) {
    startDrawingAfterClose.value = false
    drawingBoundary.value = true
    polygonTool?.open()
    return
  }
  polygonTool?.close()
  drawingBoundary.value = false
  clearDraft()
}
function cancelBoundaryDrawing() {
  polygonTool?.close()
  drawingBoundary.value = false
  dialogVisible.value = true
}
async function submit() {
  if (!canSave.value) return
  await formRef.value.validate()
  // v1.1 评审 S1：旧坐标系区域未重画（也未清除）边界时阻止保存，避免坏边界被改标渲染/下发
  if (form.id && form.originalCoordinateSystem
      && form.originalCoordinateSystem !== 'CGCS2000'
      && !boundaryRedrawn.value && !boundaryCleared.value) {
    ElMessage.error('该区域边界使用旧坐标系，必须重新圈定边界（或清除边界）后才能保存')
    return
  }
  saving.value = true
  const payload = {
    id: form.id,
    areaCode: form.areaCode.trim(),
    areaName: form.areaName.trim(),
    greenhouseName: form.greenhouseName.trim(),
    arucoId: form.arucoId,
    remark: form.remark.trim(),
    boundaryJson: boundaryCleared.value
      ? ''
      : (form.boundary.length ? JSON.stringify(form.boundary) : null),
    coordinateSystem: form.coordinateSystem
  }
  try {
    if (form.id) await updateArea(payload)
    else await addArea(payload)
    ElMessage.success('区域已保存')
    dialogVisible.value = false
    await fetchAreas()
  } finally { saving.value = false }
}
async function handleDelete(area) {
  if (!canRemove.value || !area?.id) return
  try {
    await ElMessageBox.confirm(`确定删除区域“${area.areaName}”吗？有关联业务数据的区域会被后端拒绝删除；若该区域正在录制路线，录制会话将一并取消。`, '删除确认', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  await deleteArea(area.id)
  ElMessage.success('区域已删除')
  if (form.id === area.id) dialogVisible.value = false
  await fetchAreas()
}

async function loadRecordingDevices() {
  devicesLoading.value = true
  try {
    const res = await listDevice({ pageNum: 1, pageSize: 200 })
    recordingDevices.value = (res.rows || []).filter(device => device.status === 'ONLINE')
  } catch (_) {
    recordingDevices.value = []
  } finally { devicesLoading.value = false }
}
function openRecordingSocket() {
  closeRecordingSocket()
  wsConn = connectTelemetry(onRecordingEvent)
}
function closeRecordingSocket() {
  if (wsConn) {
    wsConn.close()
    wsConn = null
  }
}
function onRecordingEvent(ev) {
  if (recording.phase !== 'recording' || ev.kind !== 'tele/state' || ev.deviceId !== recording.deviceId) return
  // v1.1 评审 L1：与后端 append 同口径过滤（数值/范围/补传旧点/连续重复/封顶），减少计数漂移
  const lat = ev.data?.lat
  const lon = ev.data?.lon
  if (typeof lat !== 'number' || typeof lon !== 'number'
      || !Number.isFinite(lat) || !Number.isFinite(lon)
      || lon < -180 || lon > 180 || lat < -90 || lat > 90) return
  const ts = typeof ev.ts === 'number' ? ev.ts : Date.now()
  if (lastFixAt.value != null && ts < lastFixAt.value) return
  if (recording.baselineCount + recordedPoints.value.length >= 20000) return
  // 与后端 RouteRecordingService.append 同口径：先按 7 位小数取整（COORDINATE_SCALE=7）再去重/入栈，
  // 保证弹窗"已录 N 点"计数与后端一致；画线也用取整后的点，视觉无差异
  const roundedLon = Number(lon.toFixed(7))
  const roundedLat = Number(lat.toFixed(7))
  const points = recordedPoints.value
  const last = points[points.length - 1]
  if (last && last[0] === roundedLon && last[1] === roundedLat) return
  points.push([roundedLon, roundedLat])
  lastFixAt.value = ts
  drawRecordingLine()
}
function clearRecordingLine() {
  if (recordingPolyline && map) map.removeOverLay(recordingPolyline)
  recordingPolyline = null
}
function drawRecordingLine() {
  if (!map || !TRef) return
  const lnglats = toLngLats(recordedPoints.value)
  if (recordingPolyline && typeof recordingPolyline.setLngLats === 'function') {
    recordingPolyline.setLngLats(lnglats)
  } else {
    clearRecordingLine()
    recordingPolyline = new TRef.Polyline(lnglats, { color: '#409eff', weight: 4, opacity: 0.9 })
    map.addOverLay(recordingPolyline)
  }
  // 只在"从 0 开始的录制"首次满 2 点时适配视野；接管（baselineCount>0）时折线只含新增段，
  // 若再跳视野会离开服务端历史点所在区域，故不再调整
  if (recordedPoints.value.length === 2 && recording.baselineCount === 0) map.setViewport(lnglats)
}
async function beginRouteRecording(area) {
  if (!canEdit.value) return
  // v1.1 评审 M1：列表摘要不返回 routeJson，覆盖确认以 hasRoute（routePointCount>=2）为准
  if (area.hasRoute) {
    try {
      await ElMessageBox.confirm(`区域“${area.areaName}”已录入路线，重新录入将覆盖现有路线。`, '录入路线', { type: 'warning' })
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      throw error
    }
  }
  // v1.1 评审 M3：接管判断前先同步录制状态，避免用旧数据误判“录制中”
  await refreshRouteRecordings()
  recording.areaId = area.id
  recording.areaCode = area.areaCode
  recording.areaName = area.areaName
  recording.deviceId = ''
  recording.baselineCount = 0
  // 评审修复#6：记录打开弹窗时的路线点数，用于 stop 超时后判定后端是否已落库
  recording.originalRoutePointCount = area.routePointCount ?? 0
  recordedPoints.value = []
  lastFixAt.value = null
  const active = recordings.value.find(item => item.areaId === area.id)
  if (active) {
    // 接管进行中的录制：恢复设备与已录点数并重连 WebSocket 观察，不重复调用开始接口
    recording.deviceId = active.deviceId
    recording.baselineCount = active.pointCount ?? 0
    // 评审修复#1 配套：状态字段改名为 lastFixTs（设备时钟），0 表示尚未收到有效点
    lastFixAt.value = typeof active.lastFixTs === 'number' && active.lastFixTs > 0 ? active.lastFixTs : null
    recording.phase = 'recording'
    recordingDialogVisible.value = true
    openRecordingSocket()
    return
  }
  recording.phase = 'select'
  recordingDialogVisible.value = true
  loadRecordingDevices()
}
async function startRecording() {
  if (recording.phase !== 'select' || !recording.areaId || !recording.deviceId || starting.value) return
  const seq = ++recordingSeq
  const areaId = recording.areaId
  starting.value = true
  try {
    await startRouteRecording(areaId, recording.deviceId)
    // v1.1 评审 L2 + 评审修复#6：await 期间弹窗已关闭/已切换区域/已有更新一轮请求时中止，
    // 不再置录制态/开 WS（before-close 已阻断在途关窗，这里作双保险）
    if (seq !== recordingSeq || !recordingDialogVisible.value || recording.areaId !== areaId) return
    recording.phase = 'recording'
    openRecordingSocket()
    await refreshRouteRecordings()
  } catch (_) {
    // 开始失败保持选择状态，错误由请求拦截器提示
  } finally { starting.value = false }
}
async function finishRecording() {
  if (recording.phase !== 'recording' || !recording.areaId || stopping.value) return
  const seq = ++recordingSeq
  const areaId = recording.areaId
  stopping.value = true
  try {
    const res = await stopRouteRecording(areaId)
    if (seq !== recordingSeq) return
    recordingDialogVisible.value = false
    ElMessage.success(`路线已保存，共 ${res.data?.pointCount ?? recording.baselineCount + recordedPoints.value.length} 点`)
    await refreshAll()
  } catch (_) {
    // 评审修复#6：保存超时/5xx 时后端可能已落库——列表与录制状态一起刷新，
    // 会话仍在（点数不足等）保持弹窗可重试；会话已消失再按路线点数是否变化判定保存结果。
    await refreshAll()
    if (seq !== recordingSeq) return
    if (recordings.value.some(item => item.areaId === areaId)) return
    recordingDialogVisible.value = false
    const area = areas.value.find(item => item.id === areaId)
    if (area?.hasRoute && (area.routePointCount ?? 0) !== recording.originalRoutePointCount) {
      ElMessage.success('路线已保存（结果在超时后确认）')
    } else {
      ElMessage.info('录制已结束，未能确认保存结果，请检查区域列表或重新录入')
    }
  } finally { stopping.value = false }
}
async function cancelRecording() {
  if (recording.phase !== 'recording' || !recording.areaId) {
    recordingDialogVisible.value = false
    return
  }
  try {
    await ElMessageBox.confirm('确定取消本次路线录入吗？已记录的点将被丢弃。', '取消录入', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  try {
    await cancelRouteRecording(recording.areaId)
  } catch (_) {
    // 取消失败保持弹窗可重试，错误由请求拦截器提示
    return
  }
  recordingDialogVisible.value = false
  ElMessage.info('已取消路线录入')
  await refreshRouteRecordings()
}
// 评审修复#6：X/ESC 关窗的统一拦截——在途请求禁止关闭；录制中由用户显式二选一，
// 避免开始请求在途时关窗留下服务端孤儿会话占用设备
async function handleRecordingDialogBeforeClose(done) {
  if (starting.value || stopping.value) {
    ElMessage.warning('请求处理中，请稍候再关闭')
    return
  }
  if (recording.phase !== 'recording') {
    done()
    return
  }
  try {
    await ElMessageBox.confirm(
      '录制仍在进行：可保持会话稍后重新打开接管，或取消本次录入（已记录的点将被丢弃）。',
      '关闭录入窗口',
      { type: 'warning', distinguishCancelAndClose: true, confirmButtonText: '保持会话并关闭', cancelButtonText: '取消本次录入' }
    )
  } catch (error) {
    if (error !== 'cancel') return // close/ESC：留在弹窗
    try {
      await cancelRouteRecording(recording.areaId)
    } catch (_) {
      return // 取消失败保持弹窗可重试，错误由请求拦截器提示
    }
    ElMessage.info('已取消路线录入')
  }
  done()
}
function handleRecordingDialogClosed() {
  recordingSeq++
  closeRecordingSocket()
  clearRecordingLine()
  recordedPoints.value = []
  lastFixAt.value = null
  recording.phase = 'select'
  recording.deviceId = ''
  recording.baselineCount = 0
  recording.originalRoutePointCount = 0
  // 直接关闭弹窗不结束录制时，刷新列表以保持“录制中”显示，可再次打开接管
  refreshRouteRecordings()
}

onMounted(() => {
  initMap()
  fetchAreas()
  refreshRouteRecordings()
})
onBeforeUnmount(() => {
  closeRecordingSocket()
  polygonTool?.close()
  clearDraft()
  clearRecordingLine()
  if (map) {
    if (map.destroy) map.destroy()
    else map.clearOverLays()
  }
})
</script>

<style scoped>
.area-page { min-height: 100%; display: flex; flex-direction: column; }
.area-page .page-heading { display: flex; align-items: center; justify-content: space-between; }
.area-workspace { min-height: 590px; flex: 1; display: grid; grid-template-columns: 390px 1fr; gap: 14px; }
.area-list { min-width: 0; }
.list-header { display: flex; align-items: center; justify-content: space-between; font-weight: 650; }
.search-input { margin-bottom: 12px; }
.map-shell { position: relative; overflow: hidden; border: 1px solid #232a22; border-radius: var(--ag-radius); background: #171c17; }
.map { position: absolute; inset: 0; }
.map-mask { position: absolute; inset: 0; display: grid; place-items: center; padding: 30px; color: #899487; text-align: center; }
.map-hint { position: absolute; z-index: 1; left: 12px; bottom: 12px; padding: 7px 10px; color: #c1c9bd; background: rgb(21 27 21 / 92%); border: 1px solid #3a4438; border-radius: 2px; font-size: 11px; }
.draw-guide { position: absolute; z-index: 2; top: 12px; left: 50%; display: flex; align-items: center; gap: 18px; min-width: 360px; padding: 10px 12px; color: #e6eae4; background: rgb(21 27 21 / 96%); border: 1px solid #409eff; border-radius: 3px; transform: translateX(-50%); box-shadow: 0 6px 18px rgb(0 0 0 / 28%); }
.draw-guide div { display: flex; flex-direction: column; gap: 2px; }
.draw-guide span { color: #aeb8ac; font-size: 11px; }
.boundary-row { display: flex; align-items: center; justify-content: space-between; width: 100%; color: var(--ag-muted); }
.boundary-actions { display: inline-flex; align-items: center; gap: 8px; }
.record-status { display: flex; align-items: center; gap: 8px; width: 100%; color: var(--ag-muted); }
.record-tip { margin-bottom: 4px; color: var(--ag-muted); font-size: 12px; line-height: 1.6; }
@media (max-width: 1120px) { .area-workspace { grid-template-columns: 1fr; } .area-list { min-height: 360px; } }
</style>
