<template>
  <div class="dashboard">
    <!-- KPI 行 -->
    <div class="kpi-row">
      <div class="panel kpi">
        <div class="kpi-value">{{ devices.length }}</div>
        <div class="kpi-label">设备总数</div>
      </div>
      <div class="panel kpi">
        <div class="kpi-value" style="color:#46e0c0">{{ onlineCount }}</div>
        <div class="kpi-label">在线设备</div>
      </div>
      <div class="panel kpi">
        <div class="kpi-value" style="color:#ffd166">{{ workingCount }}</div>
        <div class="kpi-label">作业中(AUTO)</div>
      </div>
      <div class="panel kpi">
        <div class="kpi-value" style="color:#ff6b6b">{{ alarms.length }}</div>
        <div class="kpi-label">告警数</div>
      </div>
    </div>

    <div class="main">
      <!-- 地图 -->
      <div class="panel map-panel">
        <div class="panel-title">车辆分布</div>
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-mask">{{ mapError }}</div>
      </div>

      <!-- 右侧 -->
      <div class="side">
        <div class="panel side-block">
          <div class="panel-title">状态分布</div>
          <div ref="chartEl" class="chart"></div>
        </div>
        <div class="panel side-block">
          <div class="panel-title">设备列表</div>
          <div class="dev-list">
            <div v-for="d in devices" :key="d.deviceId" class="dev-item">
              <span class="dot" :class="d.status === 'ONLINE' ? 'on' : 'off'"></span>
              <span class="dev-id">{{ d.deviceId }}</span>
              <span class="dev-name">{{ d.name }}</span>
              <span class="dev-batt">{{ d.battery != null ? d.battery + '%' : '-' }}</span>
              <span class="dev-mode">{{ d.mode || '-' }}</span>
            </div>
          </div>
        </div>
        <div class="panel side-block">
          <div class="panel-title">实时告警</div>
          <div class="alarm-list">
            <div v-if="!alarms.length" class="empty">暂无告警</div>
            <div v-for="(a, i) in alarms" :key="i" class="alarm-item">
              <span class="lv" :class="a.level">{{ a.level || 'INFO' }}</span>
              <span>{{ a.deviceId }}</span>
              <span class="msg">{{ a.msg }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { listDevice } from '@/api/device'
import { loadAMap } from '@/utils/amap'
import { connectTelemetry } from '@/utils/ws'

const devices = ref([])
const alarms = ref([])
const mapEl = ref()
const chartEl = ref()
const mapError = ref('')

let AMapRef = null
let map = null
const markers = {}
let chart = null
let wsConn = null

const onlineCount = computed(() => devices.value.filter((d) => d.status === 'ONLINE').length)
const workingCount = computed(() => devices.value.filter((d) => d.mode === 'AUTO').length)

async function loadDevices() {
  const res = await listDevice({ pageNum: 1, pageSize: 200 })
  devices.value = res.rows || []
  renderMarkers()
  renderChart()
}

function renderMarkers() {
  if (!map || !AMapRef) return
  devices.value.forEach((d) => {
    if (d.lastLat == null || d.lastLon == null) return
    const pos = [d.lastLon, d.lastLat]
    if (markers[d.deviceId]) {
      markers[d.deviceId].setPosition(pos)
    } else {
      markers[d.deviceId] = new AMapRef.Marker({
        position: pos,
        title: d.deviceId + ' ' + (d.name || ''),
        map
      })
    }
  })
}

function renderChart() {
  if (!chart) return
  const online = onlineCount.value
  const offline = devices.value.length - online
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#9db8dd' } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        label: { color: '#d6e6ff' },
        data: [
          { value: online, name: '在线', itemStyle: { color: '#46e0c0' } },
          { value: offline, name: '离线', itemStyle: { color: '#5a6b85' } }
        ]
      }
    ]
  })
}

function onEvent(ev) {
  const d = devices.value.find((x) => x.deviceId === ev.deviceId)
  if (ev.kind === 'tele/state' && d) {
    if (ev.data.lat != null) d.lastLat = ev.data.lat
    if (ev.data.lon != null) d.lastLon = ev.data.lon
    if (ev.data.battery != null) d.battery = ev.data.battery
    if (ev.data.mode) d.mode = ev.data.mode
    d.status = 'ONLINE'
    renderMarkers()
  } else if (ev.kind === 'status' && d) {
    d.status = ev.data.online ? 'ONLINE' : 'OFFLINE'
    renderChart()
  } else if (ev.kind === 'event/alarm') {
    alarms.value.unshift({ deviceId: ev.deviceId, ...ev.data })
    if (alarms.value.length > 50) alarms.value.pop()
  }
}

onMounted(async () => {
  await loadDevices()
  chart = echarts.init(chartEl.value)
  renderChart()
  try {
    AMapRef = await loadAMap()
    map = new AMapRef.Map(mapEl.value, { zoom: 12, center: [120.65, 30.12], mapStyle: 'amap://styles/darkblue' })
    renderMarkers()
  } catch (e) {
    mapError.value = e.message + '（填好 Key 后地图即显示，其余功能不受影响）'
  }
  wsConn = connectTelemetry(onEvent)
})

onBeforeUnmount(() => {
  if (wsConn) wsConn.close()
  if (chart) chart.dispose()
  if (map) map.destroy()
})
</script>

<style scoped>
.dashboard {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.kpi {
  padding: 14px 18px;
  text-align: center;
}
.main {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 12px;
  min-height: 0;
}
.map-panel {
  position: relative;
  display: flex;
  flex-direction: column;
}
.map {
  flex: 1;
  border-radius: 0 0 8px 8px;
}
.map-mask {
  position: absolute;
  inset: 40px 0 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9db8dd;
  text-align: center;
  padding: 20px;
}
.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.side-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.chart {
  flex: 1;
  min-height: 140px;
}
.dev-list,
.alarm-list {
  flex: 1;
  overflow: auto;
  padding: 6px 10px;
  font-size: 13px;
}
.dev-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px dashed rgba(64, 158, 255, 0.15);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.on {
  background: #46e0c0;
}
.dot.off {
  background: #5a6b85;
}
.dev-id {
  width: 90px;
}
.dev-name {
  flex: 1;
  color: #9db8dd;
}
.alarm-item {
  display: flex;
  gap: 8px;
  padding: 4px 0;
}
.alarm-item .lv {
  font-weight: 700;
}
.lv.CRITICAL {
  color: #ff6b6b;
}
.lv.WARN {
  color: #ffd166;
}
.msg {
  color: #9db8dd;
}
.empty {
  color: #7f9bc4;
  text-align: center;
  padding: 20px;
}
</style>
