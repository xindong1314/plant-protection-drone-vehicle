<template>
  <div class="planning">
    <div class="toolbar panel">
      <span class="panel-title" style="border:0">作业规划</span>
      <el-select v-model="deviceId" placeholder="选择设备" style="width: 200px">
        <el-option v-for="d in devices" :key="d.deviceId" :label="`${d.deviceId} ${d.name || ''}`" :value="d.deviceId" />
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

    <div class="panel map-panel">
      <div ref="mapEl" class="map"></div>
      <div v-if="mapError" class="map-mask">{{ mapError }}</div>
      <div class="hint" v-if="mapReady">已圈地块 {{ field.length }} 点 · 航线 {{ route.length }} 点</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { listDevice, assignTask } from '@/api/device'
import { loadAMap } from '@/utils/amap'

const devices = ref([])
const deviceId = ref('')
const mapEl = ref()
const mapReady = ref(false)
const mapError = ref('')
const params = reactive({ speed: 1.5, dosePerMu: 0.9 })
const field = ref([])
const route = ref([])

let AMapRef = null
let map = null
let mouseTool = null
let overlays = []

const canAssign = computed(() => deviceId.value && route.value.length > 1)

async function init() {
  const res = await listDevice({ pageNum: 1, pageSize: 200 })
  devices.value = res.rows || []
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
  mouseTool.polygon({ strokeColor: '#ffd166', fillColor: '#ffd166', fillOpacity: 0.15 })
}

function drawRoute() {
  mouseTool.polyline({ strokeColor: '#46e0c0', strokeWeight: 4 })
}

function clearAll() {
  overlays.forEach((o) => map.remove(o))
  overlays = []
  field.value = []
  route.value = []
}

async function assign() {
  const taskId = 'T-' + Date.now()
  const payload = {
    taskId,
    fieldId: 'F-LOCAL',
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
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  flex-wrap: wrap;
}
.params {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #9db8dd;
  margin-left: auto;
}
.map-panel {
  flex: 1;
  position: relative;
}
.map {
  position: absolute;
  inset: 0;
  border-radius: 8px;
}
.map-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9db8dd;
  padding: 20px;
  text-align: center;
}
.hint {
  position: absolute;
  left: 12px;
  bottom: 12px;
  background: rgba(6, 21, 41, 0.8);
  color: #7ec5ff;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
}
</style>
