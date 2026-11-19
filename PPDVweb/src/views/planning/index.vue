<template>
  <div class="planning">
    <div class="page-heading">
      <div><div class="eyebrow">MISSION PLANNING</div><h1>作业规划</h1><p>选择已录入路线的区域与在线设备，直接下发作业任务</p></div>
      <span class="live-status">规划服务可用</span>
    </div>
    <div class="toolbar">
      <span class="toolbar-label">任务配置</span>
      <el-select v-model="deviceId" placeholder="选择在线设备" style="width: 200px">
        <el-option
          v-for="d in devices"
          :key="d.deviceId"
          :label="recordingDeviceIds.has(d.deviceId) ? `${d.deviceId} ${d.name || ''}（录制路线中）` : `${d.deviceId} ${d.name || ''}`"
          :value="d.deviceId"
          :disabled="recordingDeviceIds.has(d.deviceId)"
        />
      </el-select>
      <el-select
        v-if="canListArea"
        v-model="selectedAreaId"
        clearable
        :loading="areasLoading"
        :disabled="areaListUnavailable || !mapReady"
        :placeholder="areaListUnavailable ? '区域列表不可用' : '选择作业区域'"
        style="width: 240px"
        @change="applySavedArea"
      >
        <el-option v-for="area in areas" :key="area.id" :label="`${area.areaCode} ${area.areaName || ''}`" :value="area.id" />
      </el-select>
      <span v-else class="area-fallback">无区域列表权限，无法选择作业区域</span>
      <div class="params">
        <span>速度</span><el-input-number v-model="params.speed" :min="0.1" :max="5" :step="0.1" size="small" :disabled="!canCommand" />
        <span>亩用量(L)</span><el-input-number v-model="params.dosePerMu" :min="0" :max="10" :step="0.1" size="small" :disabled="!canCommand" />
      </div>
      <el-button v-if="canCommand" type="success" :disabled="!canAssign || assigning" :loading="assigning" @click="assign">下发任务</el-button>
      <span v-if="selectedArea && !routeLoading && route.length < 2" class="no-route-tip">该区域尚未录入作业路线，请先到区域管理页录入路线</span>
    </div>

    <div class="map-shell">
    <div class="map-panel">
      <div class="map-title"><span>作业地图</span><small>AREA / ROUTE PREVIEW</small></div>
      <div ref="mapEl" class="map"></div>
      <div v-if="mapError" class="map-mask">{{ mapError }}</div>
      <div class="hint" v-if="mapReady">{{ selectedArea ? `${selectedArea.areaCode} 边界 ${field.length} 点 · 路线 ${route.length} 点` : '选择区域后预览其边界与路线' }}</div>
    </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { listDevice, assignTask } from '@/api/device'
import { getAreaRoute, listArea, listRouteRecordings } from '@/api/area'
import { parseAreaBoundary, parseRouteJson } from '@/utils/geo'
import { loadTianditu } from '@/utils/tianditu'
import { useUserStore } from '@/store/user'

const devices = ref([])
const deviceId = ref('')
const mapEl = ref()
const mapReady = ref(false)
const mapError = ref('')
const params = reactive({ speed: 1.5, dosePerMu: 0.9 })
const areas = ref([])
const areasLoading = ref(false)
const areaListUnavailable = ref(false)
const selectedAreaId = ref(null)
const field = ref([])
const route = ref([])
const assigning = ref(false)
const routeLoading = ref(false)
const recordingDeviceIds = ref(new Set())
const userStore = useUserStore()

let TRef = null
let map = null
let fieldOverlay = null
let routeOverlay = null
// 评审修复#7：区域路线请求序号——慢请求失败不得清掉后选区域的路线
let applySeq = 0

const canCommand = computed(() => userStore.hasPermission('ppdv:device:command'))
const canListArea = computed(() => userStore.hasPermission('ppdv:area:list'))
const selectedArea = computed(() => areas.value.find(area => area.id === selectedAreaId.value) || null)
const canAssign = computed(() => canCommand.value && deviceId.value && selectedAreaId.value && route.value.length >= 2)

async function initMap() {
  try {
    TRef = await loadTianditu()
    map = new TRef.Map(mapEl.value)
    map.centerAndZoom(new TRef.LngLat(120.65, 30.12), 15)
    if (window.TMAP_SATELLITE_MAP) map.setMapType(window.TMAP_SATELLITE_MAP)
    mapReady.value = true
  } catch (e) {
    mapError.value = e.message + '（作业规划依赖地图，填好 Key 后可用）'
  }
}

async function loadDevices() {
  try {
    const res = await listDevice({ pageNum: 1, pageSize: 200 })
    devices.value = (res.rows || []).filter(device => device.status === 'ONLINE')
  } catch (_) {
    devices.value = []
  }
}

async function loadAreas() {
  if (!canListArea.value) return
  areasLoading.value = true
  areaListUnavailable.value = false
  try {
    const res = await listArea()
    areas.value = res.data || []
  } catch (_) {
    areas.value = []
    areaListUnavailable.value = true
  } finally {
    areasLoading.value = false
  }
  // v1.1 评审 L4：正在录制路线的设备在下拉中禁用，避免下发任务污染当次录制
  try {
    const res = await listRouteRecordings()
    recordingDeviceIds.value = new Set((res.data || []).map(item => item.deviceId))
  } catch (_) {
    recordingDeviceIds.value = new Set()
  }
}

function toLngLats(points) {
  return points.map((p) => new TRef.LngLat(p[0], p[1]))
}

function removeOverlay(overlay) {
  if (overlay && map) map.removeOverLay(overlay)
}

function clearPreview() {
  removeOverlay(fieldOverlay)
  removeOverlay(routeOverlay)
  fieldOverlay = null
  routeOverlay = null
  field.value = []
  route.value = []
}

async function applySavedArea(id) {
  const seq = ++applySeq
  clearPreview()
  if (id === null || id === undefined || id === '') return
  const area = areas.value.find(item => item.id === id)
  if (!area) {
    ElMessage.warning('未找到所选区域')
    return
  }
  // 区域边界为多边形（去闭环）；路线为航线（保留首尾闭合）
  const boundary = parseAreaBoundary(area)
  field.value = boundary
  // v1.1 评审 M1：列表摘要不再携带 routeJson，选中后按 id 拉取完整路线
  routeLoading.value = true
  try {
    const detail = await getAreaRoute(id)
    // await 期间用户已切换/清空选择或已有更新请求时丢弃过期响应
    if (seq !== applySeq || selectedAreaId.value !== id) return
    route.value = parseRouteJson(detail.data?.routeJson)
  } catch (_) {
    // 评审修复#7：陈旧的失败响应同样丢弃，不得清空新选区域的路线
    if (seq !== applySeq || selectedAreaId.value !== id) return
    route.value = []
  } finally {
    if (seq === applySeq) routeLoading.value = false
  }
  if (seq !== applySeq || selectedAreaId.value !== id) return
  if (boundary.length >= 3) {
    fieldOverlay = new TRef.Polygon(toLngLats(boundary), { color: '#c77700', weight: 2, opacity: 0.9, fillColor: '#c77700', fillOpacity: 0.18 })
    map.addOverLay(fieldOverlay)
  }
  if (route.value.length >= 2) {
    routeOverlay = new TRef.Polyline(toLngLats(route.value), { color: '#76b900', weight: 4, opacity: 0.9 })
    map.addOverLay(routeOverlay)
  }
  const focus = boundary.length ? boundary : route.value
  if (focus.length && map && TRef) map.setViewport(toLngLats(focus))
}

async function assign() {
  if (!canAssign.value || assigning.value) return
  const area = selectedArea.value
  if (!area) return
  if (recordingDeviceIds.value.has(deviceId.value)) {
    // v1.1 评审 L4：录制中的设备不允许下发（后端同样 409 兜底）
    ElMessage.warning('该设备正在录制路线，不能下发任务')
    return
  }
  if (!field.value.length) ElMessage.warning('该区域尚未圈定边界，仅下发路线')
  const taskId = 'T-' + Date.now()
  const payload = {
    taskId,
    fieldId: `AREA-${area.id}`,
    areaId: area.id,
    route: route.value,
    field: field.value,
    params: { speed: params.speed, dosePerMu: params.dosePerMu }
  }
  assigning.value = true
  try {
    const res = await assignTask(deviceId.value, payload)
    ElMessage.success(`任务 ${taskId} 已下发 (msgId=${res.data})`)
  } catch (e) {
    /* handled */
  } finally {
    // v1.1 评审 L5：下发期间按钮 loading + 重入保护，防快速双击重复下发
    assigning.value = false
  }
}

onMounted(() => {
  initMap()
  loadDevices()
  loadAreas()
})
onBeforeUnmount(() => {
  removeOverlay(fieldOverlay)
  removeOverlay(routeOverlay)
  if (map) {
    if (map.destroy) map.destroy()
    else map.clearOverLays()
  }
})
</script>

<style scoped>
.planning {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid var(--ag-border);
  border-radius: var(--ag-radius);
  margin-bottom: 12px;
}
.toolbar-label { margin-right: 4px; color: var(--ag-muted); font-size: 12px; font-weight: 650; }
.area-fallback { color: var(--ag-muted); font-size: 11px; }
.params {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ag-muted);
  font-size: 12px;
  margin-left: auto;
}
.no-route-tip {
  color: #c77700;
  font-size: 12px;
}
.map-shell { min-height: 560px; flex: 1; padding: 10px; position: relative; background: #0d110d; border: 1px solid #232a22; border-radius: var(--ag-radius); }
.map-panel {
  position: absolute;
  inset: 10px;
  overflow: hidden;
  background: #171c17;
  border: 1px solid #303830;
  border-radius: 2px;
}
.map-title {
  position: absolute;
  z-index: 2;
  top: 0;
  left: 0;
  right: 0;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 15px;
  color: #e6eae4;
  background: #171c17;
  border-bottom: 1px solid #303830;
  font-size: 12px;
  font-weight: 650;
}
.map-title small { color: #687367; font-size: 9px; letter-spacing: 1px; }
.map {
  position: absolute;
  inset: 46px 0 0;
}
.map-mask {
  position: absolute;
  inset: 46px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #899487;
  padding: 20px;
  text-align: center;
}
.hint {
  position: absolute;
  left: 12px;
  bottom: 12px;
  background: #151b15;
  color: #b7c2b5;
  border: 1px solid #3a4438;
  padding: 7px 10px;
  border-radius: 2px;
  font-size: 11px;
}
</style>
