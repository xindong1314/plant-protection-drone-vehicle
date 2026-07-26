<template>
  <div class="cockpit">
    <div class="toolbar panel">
      <span class="panel-title" style="border:0">驾驶舱</span>
      <el-select v-model="selectedId" placeholder="选择设备" style="width: 220px" @change="onSelect">
        <el-option v-for="d in devices" :key="d.deviceId" :label="`${d.deviceId} ${d.name || ''}`" :value="d.deviceId" />
      </el-select>
      <div class="cmd-btns" v-if="selectedId">
        <el-button size="small" type="success" @click="cmd('START')">启动</el-button>
        <el-button size="small" type="warning" @click="cmd('PAUSE')">暂停</el-button>
        <el-button size="small" @click="cmd('RESUME')">继续</el-button>
        <el-button size="small" type="info" @click="cmd('RTL')">返航</el-button>
        <el-button size="small" type="danger" @click="cmd('ESTOP')">急停</el-button>
      </div>
    </div>

    <div class="body">
      <div class="left">
        <div class="panel gauge-panel">
          <div class="panel-title">实时状态</div>
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
      </div>
      <div class="panel map-panel">
        <div class="panel-title">行驶轨迹</div>
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-mask">{{ mapError }}</div>
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

const devices = ref([])
const selectedId = ref('')
const state = reactive({ battery: null, speed: null, heading: null, mode: '', lat: null, lon: null })
const online = ref(false)
const gaugeEl = ref()
const mapEl = ref()
const mapError = ref('')

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
        progress: { show: true, width: 14 },
        axisLine: { lineStyle: { width: 14 } },
        axisLabel: { color: '#9db8dd', distance: 16 },
        detail: { valueAnimation: true, formatter: '{value}%', color: '#46e0c0', fontSize: 26 },
        data: [{ value: state.battery ?? 0, name: '电量' }],
        title: { color: '#9db8dd' }
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
    polyline = new AMapRef.Polyline({ path: track, strokeColor: '#46e0c0', strokeWeight: 4, map })
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
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
}
.cmd-btns {
  margin-left: auto;
}
.body {
  flex: 1;
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 12px;
  min-height: 0;
}
.gauge-panel,
.map-panel {
  display: flex;
  flex-direction: column;
  position: relative;
}
.gauge {
  height: 220px;
}
.stat-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 12px 16px;
}
.stat-grid .k {
  color: #9db8dd;
  margin-right: 8px;
}
.stat-grid .v {
  color: #d6e6ff;
  font-weight: 600;
}
.map {
  flex: 1;
}
.map-mask {
  position: absolute;
  inset: 40px 0 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9db8dd;
  padding: 20px;
  text-align: center;
}
</style>
