<template>
  <div class="admin-home">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">ADMIN OVERVIEW</div><h1>运营管理工作台</h1><p>设备资产、系统权限与运行审计的统一管理入口</p></div>
      <span class="admin-live">管理服务运行正常</span>
    </div>

    <section class="metric-grid">
      <div class="metric-card"><span class="metric-code">FLEET</span><strong>{{ metrics.total }}</strong><p>登记设备</p></div>
      <div class="metric-card"><span class="metric-code">ONLINE</span><strong class="positive">{{ metrics.online }}</strong><p>当前在线</p></div>
      <div class="metric-card"><span class="metric-code">OFFLINE</span><strong>{{ metrics.offline }}</strong><p>当前离线</p></div>
      <div class="metric-card"><span class="metric-code">ENERGY</span><strong>{{ metrics.battery }}</strong><p>平均电量</p></div>
    </section>

    <section class="home-grid">
      <div class="surface-card operations-card">
        <div class="section-head"><div><span>常用操作</span><small>OPERATION SHORTCUTS</small></div></div>
        <div class="shortcut-grid">
          <router-link v-for="item in shortcuts" :key="item.path" :to="item.path" class="shortcut">
            <span class="shortcut-icon"><svg-icon :icon-class="item.icon" /></span>
            <span><strong>{{ item.title }}</strong><small>{{ item.desc }}</small></span>
            <i>→</i>
          </router-link>
        </div>
      </div>

      <div class="surface-card security-card">
        <div class="section-head"><div><span>管理原则</span><small>ADMINISTRATION POLICY</small></div></div>
        <div class="policy-list">
          <div><b>01</b><span><strong>最小权限</strong><small>按岗位分配角色与菜单权限</small></span></div>
          <div><b>02</b><span><strong>操作留痕</strong><small>高风险操作应检查审计日志</small></span></div>
          <div><b>03</b><span><strong>设备密钥</strong><small>重置后及时同步车载端配置</small></span></div>
        </div>
      </div>
    </section>

    <section class="surface-card fleet-card">
      <div class="section-head">
        <div><span>设备状态快照</span><small>FLEET STATUS SNAPSHOT</small></div>
        <router-link to="/ppdv/device">查看全部设备 →</router-link>
      </div>
      <el-table v-loading="loading" :data="devices.slice(0, 6)" empty-text="暂无设备数据">
        <el-table-column prop="deviceId" label="设备编号" min-width="130" />
        <el-table-column prop="name" label="设备名称" min-width="130" />
        <el-table-column prop="model" label="型号" min-width="110" />
        <el-table-column prop="status" label="在线状态" width="110">
          <template #default="{ row }"><el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" size="small">{{ row.status === 'ONLINE' ? '在线' : '离线' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="battery" label="电量" width="150">
          <template #default="{ row }"><el-progress v-if="row.battery != null" :percentage="row.battery" :stroke-width="10" :color="batteryColor(row.battery)" /><span v-else class="muted">未上报</span></template>
        </el-table-column>
        <el-table-column prop="mode" label="作业模式" width="110" />
        <el-table-column prop="lastSeenAt" label="最近在线" min-width="165"><template #default="{ row }">{{ parseTime(row.lastSeenAt) || '-' }}</template></el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup name="Index">
import { listDevice } from '@/api/ppdv/device'

const loading = ref(false)
const devices = ref([])
const metrics = computed(() => {
  const validBattery = devices.value.filter(item => item.battery != null)
  const online = devices.value.filter(item => item.status === 'ONLINE').length
  const average = validBattery.length
    ? Math.round(validBattery.reduce((sum, item) => sum + Number(item.battery), 0) / validBattery.length) + '%'
    : '-'
  return { total: devices.value.length, online, offline: devices.value.length - online, battery: average }
})
const shortcuts = [
  { title: '设备管理', desc: '登记、密钥与控制指令', path: '/ppdv/device', icon: 'monitor' },
  { title: '实时状态', desc: '在线状态与遥测历史', path: '/ppdv/realtime', icon: 'dashboard' },
  { title: '用户权限', desc: '账号、角色与数据范围', path: '/system/user', icon: 'user' },
  { title: '操作日志', desc: '查询关键管理操作', path: '/monitor/operlog', icon: 'form' }
]

function batteryColor(value) {
  if (value >= 60) return '#76b900'
  if (value >= 20) return '#c77700'
  return '#d93025'
}

async function loadDevices() {
  loading.value = true
  try {
    const response = await listDevice({ pageNum: 1, pageSize: 100 })
    devices.value = response.rows || []
  } catch {
    devices.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadDevices)
</script>

<style scoped lang="scss">
.admin-home { padding: 24px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
.metric-card { min-height: 126px; display: flex; flex-direction: column; justify-content: center; padding: 19px; background: #fff; border: 1px solid var(--ag-border); border-radius: 4px; }
.metric-code { color: #899287; font-size: 9px; font-weight: 700; letter-spacing: 1.2px; }
.metric-card strong { margin-top: 9px; color: var(--ag-ink); font-size: 34px; line-height: 1; letter-spacing: -1.5px; }
.metric-card strong.positive { color: #5f9500; }
.metric-card p { margin: 9px 0 0; color: var(--ag-muted); font-size: 11px; }
.home-grid { display: grid; grid-template-columns: 1.7fr 1fr; gap: 14px; margin-bottom: 14px; }
.surface-card { background: #fff; border: 1px solid var(--ag-border); border-radius: 4px; }
.section-head { min-height: 54px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; border-bottom: 1px solid var(--ag-border); }
.section-head span, .section-head small { display: block; }
.section-head span { color: var(--ag-ink); font-size: 13px; font-weight: 650; }
.section-head small { margin-top: 3px; color: #91998f; font-size: 8px; letter-spacing: 1px; }
.section-head > a { color: #5f9500; font-size: 11px; font-weight: 600; }
.shortcut-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1px; padding: 1px; background: var(--ag-border); }
.shortcut { min-height: 78px; display: flex; align-items: center; gap: 11px; padding: 14px; background: #fff; transition: background .15s; }
.shortcut:hover { background: #f4f8ee; }
.shortcut-icon { width: 34px; height: 34px; flex: 0 0 34px; display: grid; place-items: center; color: #5f9500; background: var(--ag-primary-soft); font-size: 17px; }
.shortcut strong, .shortcut small { display: block; }
.shortcut strong { color: var(--ag-ink); font-size: 12px; }
.shortcut small { margin-top: 5px; color: var(--ag-muted); font-size: 10px; }
.shortcut i { margin-left: auto; color: #a1a99f; font-style: normal; }
.policy-list { padding: 4px 16px; }
.policy-list > div { display: flex; align-items: flex-start; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--ag-border); }
.policy-list > div:last-child { border: 0; }
.policy-list b { color: #76b900; font-size: 10px; }
.policy-list strong, .policy-list small { display: block; }
.policy-list strong { color: var(--ag-ink); font-size: 11px; }
.policy-list small { margin-top: 5px; color: var(--ag-muted); font-size: 10px; }
.fleet-card { overflow: hidden; }
.fleet-card :deep(.el-table) { border: 0; }
.muted { color: var(--ag-muted); font-size: 11px; }
@media (max-width: 1100px) { .home-grid { grid-template-columns: 1fr; } }
</style>
