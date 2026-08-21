<template>
  <div class="cockpit">
    <div class="page-heading">
      <div><div class="eyebrow">LIVE TELEMETRY</div><h1>数字驾驶舱</h1><p>查看车辆姿态、能量状态和实时行驶轨迹</p></div>
      <span class="live-status">遥测通道在线</span>
    </div>
    <div class="toolbar">
      <span class="toolbar-label">当前设备</span>
      <el-select v-model="selectedId" placeholder="选择设备" style="width: 220px" @change="onSelect">
        <el-option v-for="d in devices" :key="d.deviceId" :label="`${d.deviceId} ${d.name || ''}`" :value="d.deviceId" />
      </el-select>
      <div class="cmd-btns" v-if="selectedId && canCommand">
        <el-button size="small" type="success" @click="cmd('START')">启动</el-button>
        <el-button size="small" type="warning" @click="cmd('PAUSE')">暂停</el-button>
        <el-button size="small" @click="cmd('RESUME')">继续</el-button>
        <el-button size="small" type="info" @click="cmd('RTL')">返航</el-button>
        <el-button size="small" type="danger" @click="cmd('ESTOP')">急停</el-button>
      </div>
    </div>

    <div class="cockpit-stage">
      <div class="body">
        <div class="gauge-panel dark-panel">
          <div class="dark-title"><span>车辆状态</span><small>{{ selectedId || 'NO DEVICE' }}</small></div>
          <div ref="gaugeEl" class="gauge"></div>
          <div class="stat-grid">
            <div><span class="k">模式</span><span class="v">{{ state.mode || '-' }}</span></div>
            <div><span class="k">速度</span><span class="v">{{ fmt(state.speed) }} m/s</span></div>
            <div><span class="k">航向</span><span class="v">{{ fmt(state.heading) }}°</span></div>
            <div><span class="k">状态</span><span class="v">{{ online ? '在线' : '离线' }}</span></div>
            <div><span class="k">纬度</span><span class="v">{{ fmt(state.lat, 6) }}</span></div>
            <div><span class="k">经度</span><span class="v">{{ fmt(state.lon, 6) }}</span></div>
          </div>
        </div>
        <div class="map-panel dark-panel">
          <div class="dark-title"><span>实时轨迹</span><small>POSITION / ROUTE</small></div>
          <div ref="mapEl" class="map"></div>
          <div v-if="mapError" class="map-mask">{{ mapError }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { listDevice, stateHistory, sendCommand } from '@/api/device'
import { loadAMap } from '@/utils/amap'
import { connectTelemetry } from '@/utils/ws'
import { useUserStore } from '@/store/user'

const devices = ref([])
const selectedId = ref('')
const state = reactive({ battery: null, speed: null, heading: null, mode: '', lat: null, lon: null })
const online = ref(false)
const gaugeEl = ref()
const mapEl = ref()
const mapError = ref('')
const userStore = useUserStore()
const canCommand = computed(() => userStore.hasPermission('ppdv:device:command'))

let gauge = null
let AMapRef = null
let map = null
let polyline = null
let track = []
let wsConn = null

const fmt = (v, n = 1) => (v == null ? '-' : Number(v).toFixed(n))

async function init() {
  const res = await listDevice({ pageNum: 1, pageSize: 200 })
  devices.value = res.rows || []
  gauge = echarts.init(gaugeEl.value)
  renderGauge()
  try {
    AMapRef = await loadAMap()
    map = new AMapRef.Map(mapEl.value, { zoom: 15, center: [120.65, 30.12], mapStyle: 'amap://styles/darkblue' })
  } catch (e) {
    mapError.value = e.message + '（填好 Key 后地图即显示）'
  }
  wsConn = connectTelemetry(onEvent)
}

function renderGauge() {
  if (!gauge) return
  gauge.setOption({
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 100,
        progress: { show: true, width: 12, itemStyle: { color: '#76b900' } },
        axisLine: { lineStyle: { width: 12, color: [[1, '#30382f']] } },
        axisLabel: { color: '#7f897e', distance: 16 },
        detail: { valueAnimation: true, formatter: '{value}%', color: '#f2f5f0', fontSize: 27 },
        data: [{ value: state.battery ?? 0, name: '电量' }],
        title: { color: '#7f897e' },
        pointer: { itemStyle: { color: '#76b900' } }
      }
    ]
  })
}

async function onSelect(id) {
  track = []
  if (polyline) {
    polyline.setPath([])
  }
  const d = devices.value.find((x) => x.deviceId === id)
  if (d) {
    state.battery = d.battery
    state.mode = d.mode
    state.lat = d.lastLat
    state.lon = d.lastLon
    online.value = d.status === 'ONLINE'
    renderGauge()
  }
  // 历史轨迹
  const res = await stateHistory(id, 200)
  const rows = (res.data || []).reverse()
  track = rows.filter((r) => r.lat != null && r.lon != null).map((r) => [r.lon, r.lat])
  drawTrack()
}

function drawTrack() {
  if (!map || !AMapRef || !track.length) return
  if (!polyline) {
    polyline = new AMapRef.Polyline({ path: track, strokeColor: '#76b900', strokeWeight: 4, map })
  } else {
    polyline.setPath(track)
  }
  map.setFitView([polyline])
}

function onEvent(ev) {
  if (ev.deviceId !== selectedId.value) return
  if (ev.kind === 'tele/state') {
    Object.assign(state, {
      battery: ev.data.battery ?? state.battery,
      speed: ev.data.speed ?? state.speed,
      heading: ev.data.heading ?? state.heading,
      mode: ev.data.mode || state.mode,
      lat: ev.data.lat ?? state.lat,
      lon: ev.data.lon ?? state.lon
    })
    online.value = true
    renderGauge()
    if (ev.data.lat != null && ev.data.lon != null) {
      track.push([ev.data.lon, ev.data.lat])
      drawTrack()
    }
  } else if (ev.kind === 'status') {
    online.value = !!ev.data.online
  }
}

async function cmd(c) {
  try {
    await sendCommand(selectedId.value, { cmd: c })
    ElMessage.success(c + ' 指令已下发')
  } catch (e) {
    /* handled */
  }
}

onMounted(init)
onBeforeUnmount(() => {
  if (wsConn) wsConn.close()
  if (gauge) gauge.dispose()
  if (map) map.destroy()
})
</script>

<style scoped>
.cockpit {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.cockpit .page-heading {
  flex: 0 0 auto;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--ag-border);
  border-radius: var(--ag-radius);
  margin-bottom: 12px;
}
.toolbar-label { color: var(--ag-muted); font-size: 12px; font-weight: 600; }
.cmd-btns {
  margin-left: auto;
}
.cockpit-stage { min-height: 545px; flex: 1; padding: 10px; background: #0d110d; border: 1px solid #232a22; border-radius: var(--ag-radius); }
.body {
  height: 100%;
  display: grid;
  grid-template-columns: 330px 1fr;
  gap: 10px;
  min-height: 0;
}
.dark-panel { background: #171c17; border: 1px solid #303830; border-radius: 2px; }
.gauge-panel, .map-panel {
  display: flex;
  flex-direction: column;
  position: relative;
}
.dark-title { height: 46px; flex: 0 0 46px; display: flex; align-items: center; justify-content: space-between; padding: 0 15px; color: #e6eae4; border-bottom: 1px solid #303830; font-size: 12px; font-weight: 650; }
.dark-title small { color: #687367; font-size: 9px; letter-spacing: 1px; }
.gauge {
  height: 250px;
}
.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  margin: 0 14px 14px;
  background: #303830;
  border: 1px solid #303830;
}
.stat-grid > div {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 12px;
  background: #1b211b;
}
.stat-grid .k {
  color: #707b70;
  font-size: 10px;
}
.stat-grid .v {
  color: #e5e9e3;
  font-family: monospace;
  font-weight: 600;
}
.map { flex: 1; min-height: 450px; }
.map-mask {
  position: absolute;
  inset: 46px 0 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #899487;
  padding: 20px;
  text-align: center;
}
</style>
