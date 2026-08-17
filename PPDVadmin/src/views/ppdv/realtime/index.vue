<template>
  <div class="app-container">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">LIVE TELEMETRY</div><h1>实时状态</h1><p>监控车辆在线状态、位置与遥测历史</p></div>
      <span class="admin-live">{{ auto ? '每 5 秒自动刷新' : '手动刷新模式' }}</span>
    </div>
    <div class="realtime-summary">
      <div><small>设备总数</small><strong>{{ deviceList.length }}</strong></div>
      <div><small>当前在线</small><strong class="positive">{{ onlineCount }}</strong></div>
      <div><small>当前离线</small><strong>{{ deviceList.length - onlineCount }}</strong></div>
    </div>
    <el-row :gutter="10" class="mb8 action-bar">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Refresh" @click="getList">刷新</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-switch v-model="auto" active-text="自动刷新(5s)" @change="toggleAuto" />
      </el-col>
      <el-col :span="6" class="online-copy">
        在线 {{ onlineCount }} / 共 {{ deviceList.length }} 台
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="deviceList">
      <el-table-column label="设备编号" align="center" prop="deviceId" />
      <el-table-column label="名称" align="center" prop="name" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="ppdv_device_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="电量%" align="center" prop="battery" />
      <el-table-column label="作业模式" align="center" prop="mode">
        <template #default="scope">
          <dict-tag :options="ppdv_work_mode" :value="scope.row.mode" />
        </template>
      </el-table-column>
      <el-table-column label="纬度" align="center" prop="lastLat" />
      <el-table-column label="经度" align="center" prop="lastLon" />
      <el-table-column label="最近在线" align="center" prop="lastSeenAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastSeenAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="TrendCharts" @click="showHistory(scope.row)">遥测历史</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="'遥测历史 - ' + curDevice" v-model="histOpen" width="760px" append-to-body>
      <el-table :data="history" height="420" v-loading="histLoading">
        <el-table-column label="时间" align="center" prop="ts" width="180" />
        <el-table-column label="纬度" align="center" prop="lat" />
        <el-table-column label="经度" align="center" prop="lon" />
        <el-table-column label="航向" align="center" prop="heading" />
        <el-table-column label="速度" align="center" prop="speed" />
        <el-table-column label="电量%" align="center" prop="battery" />
        <el-table-column label="模式" align="center" prop="mode" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="Realtime">
import { listDevice, stateHistory } from "@/api/ppdv/device"

const { proxy } = getCurrentInstance()
const { ppdv_device_status, ppdv_work_mode } = useDict("ppdv_device_status", "ppdv_work_mode")

const deviceList = ref([])
const loading = ref(true)
const auto = ref(false)
const histOpen = ref(false)
const histLoading = ref(false)
const history = ref([])
const curDevice = ref("")
let timer = null

const onlineCount = computed(() => deviceList.value.filter(d => d.status === "ONLINE").length)

function getList() {
  loading.value = true
  listDevice({ pageNum: 1, pageSize: 100 }).then(response => {
    deviceList.value = response.rows
    loading.value = false
  }).catch(() => { loading.value = false })
}

function toggleAuto(val) {
  if (val) {
    timer = setInterval(getList, 5000)
  } else if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function showHistory(row) {
  curDevice.value = row.deviceId
  histOpen.value = true
  histLoading.value = true
  stateHistory(row.deviceId, 200).then(res => {
    history.value = res.data
    histLoading.value = false
  }).catch(() => { histLoading.value = false })
}

onMounted(getList)
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped lang="scss">
.realtime-summary { display: grid; grid-template-columns: repeat(3, minmax(150px, 220px)); gap: 10px; margin-bottom: 12px; }
.realtime-summary > div { padding: 14px 16px; background: #fff; border: 1px solid var(--ag-border); border-radius: 4px; }
.realtime-summary small, .realtime-summary strong { display: block; }
.realtime-summary small { color: var(--ag-muted); font-size: 10px; }
.realtime-summary strong { margin-top: 7px; color: var(--ag-ink); font-size: 25px; line-height: 1; }
.realtime-summary strong.positive { color: #5f9500; }
.action-bar { padding: 10px 12px; background: #fff; border: 1px solid var(--ag-border); border-radius: 4px; }
.online-copy { line-height: 32px; color: var(--ag-muted); font-size: 11px; }
</style>
