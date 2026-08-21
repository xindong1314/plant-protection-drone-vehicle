<template>
  <div class="planning">
    <div class="page-heading">
      <div><div class="eyebrow">MISSION PLANNING</div><h1>作业规划</h1><p>绘制作业地块与车辆路线，并设置任务参数</p></div>
      <span class="live-status">规划服务可用</span>
    </div>
    <div class="toolbar">
      <span class="toolbar-label">任务配置</span>
      <el-select v-model="deviceId" placeholder="选择设备" style="width: 200px">
        <el-option v-for="d in devices" :key="d.deviceId" :label="`${d.deviceId} ${d.name || ''}`" :value="d.deviceId" />
      </el-select>
      <el-select v-model="selectedAreaId" clearable placeholder="选择已保存区域" style="width: 200px" @change="applySavedArea">
        <el-option v-for="area in areas" :key="area.id" :label="`${area.areaCode} ${area.areaName || ''}`" :value="area.id" />
      </el-select>
      <el-button size="small" :disabled="!mapReady" @click="drawField">圈地块</el-button>
      <el-button size="small" type="primary" :disabled="!mapReady" @click="drawRoute">画航线</el-button>
      <el-button size="small" @click="clearAll" :disabled="!mapReady">清除</el-button>
      <div class="params">
        <span>速度</span><el-input-number v-model="params.speed" :min="0.1" :max="5" :step="0.1" size="small" />
        <span>亩用量(L)</span><el-input-number v-model="params.dosePerMu" :min="0" :max="10" :step="0.1" size="small" />
      </div>
      <el-button type="success" :disabled="!canAssign" @click="assign">下发任务</el-button>
    </div>

    <div class="map-shell">
    <div class="map-panel">
      <div class="map-title"><span>作业地图</span><small>FIELD / ROUTE EDITOR</small></div>
      <div ref="mapEl" class="map"></div>
      <div v-if="mapError" class="map-mask">{{ mapError }}</div>
      <div class="hint" v-if="mapReady">已圈地块 {{ field.length }} 点 · 航线 {{ route.length }} 点</div>
    </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { listDevice, assignTask } from '@/api/device'
import { listArea } from '@/api/area'
import { loadAMap } from '@/utils/amap'

const devices = ref([])
const deviceId = ref('')
const mapEl = ref()
const mapReady = ref(false)
const mapError = ref('')
const params = reactive({ speed: 1.5, dosePerMu: 0.9 })
const field = ref([])
const route = ref([])
const areas = ref([])
const selectedAreaId = ref(null)

let AMapRef = null
let map = null
let mouseTool = null
let overlays = []

const canAssign = computed(() => deviceId.value && route.value.length > 1)

async function init() {
  const res = await listDevice({ pageNum: 1, pageSize: 200 })
  devices.value = res.rows || []
  const areaRes = await listArea()
  areas.value = areaRes.data || []
  try {
    AMapRef = await loadAMap()
    map = new AMapRef.Map(mapEl.value, { zoom: 15, center: [120.65, 30.12], mapStyle: 'amap://styles/darkblue' })
    mouseTool = new AMapRef.MouseTool(map)
    mouseTool.on('draw', onDraw)
    mapReady.value = true
  } catch (e) {
    mapError.value = e.message + '（作业规划依赖地图，填好 Key 后可用）'
  }
}

function pathToArr(path) {
  return path.map((p) => [p.getLng(), p.getLat()])
}

function onDraw(e) {
  const obj = e.obj
  overlays.push(obj)
  const arr = pathToArr(obj.getPath())
  if (obj.CLASS_NAME && obj.CLASS_NAME.indexOf('Polygon') >= 0) {
    field.value = arr
  } else {
    route.value = arr
  }
  mouseTool.close(false)
}

function drawField() {
  mouseTool.polygon({ strokeColor: '#c77700', fillColor: '#c77700', fillOpacity: 0.18 })
}

function drawRoute() {
  mouseTool.polyline({ strokeColor: '#76b900', strokeWeight: 4 })
}

function clearAll() {
  overlays.forEach((o) => map.remove(o))
  overlays = []
  field.value = []
  route.value = []
  selectedAreaId.value = null
}

function applySavedArea(id) {
  const area = areas.value.find(item => item.id === id)
  if (!area) return
  try {
    const boundary = JSON.parse(area.boundaryJson || '[]')
    if (!Array.isArray(boundary) || boundary.length < 3) {
      ElMessage.warning('该区域尚未配置 GIS 地块边界')
      return
    }
    field.value = boundary
    const polygon = new AMapRef.Polygon({ path: boundary, strokeColor: '#c77700', fillColor: '#c77700', fillOpacity: 0.18 })
    overlays.push(polygon)
    map.add(polygon)
    map.setFitView([polygon])
  } catch (_) { ElMessage.warning('区域边界数据格式错误') }
}

async function assign() {
  const taskId = 'T-' + Date.now()
  const payload = {
    taskId,
    fieldId: selectedAreaId.value ? `AREA-${selectedAreaId.value}` : 'F-LOCAL',
    areaId: selectedAreaId.value,
    route: route.value,
    field: field.value,
    params: { speed: params.speed, dosePerMu: params.dosePerMu }
  }
  try {
    const res = await assignTask(deviceId.value, payload)
    ElMessage.success(`任务 ${taskId} 已下发 (msgId=${res.data})`)
  } catch (e) {
    /* handled */
  }
}

onMounted(init)
onBeforeUnmount(() => {
  if (map) map.destroy()
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
.params {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--ag-muted);
  font-size: 12px;
  margin-left: auto;
}
.map-shell { min-height: 560px; flex: 1; padding: 10px; background: #0d110d; border: 1px solid #232a22; border-radius: var(--ag-radius); }
.map-panel {
  height: 100%;
  position: relative;
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
