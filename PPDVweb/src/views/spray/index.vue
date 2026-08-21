<template>
  <div class="spray-page">
    <div class="page-heading">
      <div><div class="eyebrow">PRECISION SPRAY</div><h1>点喷任务看板</h1><p>创建、分配和跟踪高风险区域的精准喷洒任务</p></div>
      <el-button type="primary" :icon="CirclePlus" @click="openCreate">创建点喷任务</el-button>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col v-for="item in statusSummary" :key="item.value" :span="6"><el-card><div class="summary-label">{{ item.label }}</div><div class="summary-value" :class="item.value.toLowerCase()">{{ statusCount(item.value) }}</div></el-card></el-col>
    </el-row>

    <section class="surface-card board-card">
      <div class="toolbar"><el-select v-model="filterStatus" clearable placeholder="全部状态" style="width:160px"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-input v-model="keyword" clearable placeholder="搜索设备或区域" :prefix-icon="Search" /><el-button :icon="Refresh" @click="loadTasks">刷新</el-button></div>
      <el-table v-loading="loading" :data="filteredTasks">
        <el-table-column prop="id" label="任务号" width="90" />
        <el-table-column label="目标区域" min-width="165"><template #default="{ row }">{{ areaName(row.areaId) }}</template></el-table-column>
        <el-table-column prop="deviceId" label="执行设备" min-width="145"><template #default="{ row }">{{ row.deviceId || '待分配' }}</template></el-table-column>
        <el-table-column label="任务状态" width="130"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
        <el-table-column label="完成时间" min-width="170"><template #default="{ row }">{{ formatTime(row.completedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right"><template #default="{ row }"><el-button v-if="row.status === 'PENDING'" type="primary" link @click="openAssign(row)">分配设备</el-button><el-button v-if="row.status === 'ASSIGNED'" type="primary" link @click="openExecute(row)">执行点喷</el-button><el-button v-if="row.status === 'IN_PROGRESS'" type="success" link @click="setStatus(row, 'COMPLETED')">标记完成</el-button><el-button v-if="['PENDING', 'ASSIGNED', 'IN_PROGRESS'].includes(row.status)" type="danger" link @click="setStatus(row, 'FAILED')">标记失败</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createVisible" title="创建点喷任务" width="460px" destroy-on-close>
      <el-form :model="createForm" label-width="90px"><el-form-item label="目标区域" required><el-select v-model="createForm.areaId" filterable placeholder="选择高风险区域"><el-option v-for="area in areas" :key="area.id" :label="areaLabel(area)" :value="area.id" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="分配执行设备" width="460px" destroy-on-close>
      <el-form label-width="90px"><el-form-item label="任务"><span>#{{ activeTask?.id }} · {{ areaName(activeTask?.areaId) }}</span></el-form-item><el-form-item label="执行设备" required><el-select v-model="selectedDeviceId" filterable placeholder="选择设备"><el-option v-for="device in devices" :key="device.deviceId" :label="`${device.name || device.deviceId}（${device.deviceId}）`" :value="device.deviceId" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="assignVisible = false">取消</el-button><el-button type="primary" :loading="assigning" @click="submitAssign">确认分配</el-button></template>
    </el-dialog>

    <el-dialog v-model="executeVisible" title="下发点喷指令" width="480px" destroy-on-close>
      <el-alert title="确认后将通过 MQTT 向已分配设备下发点喷启动指令，任务状态会更新为“执行中”。" type="warning" :closable="false" show-icon class="execute-alert" />
      <el-form :model="executeForm" label-width="100px"><el-form-item label="任务"><span>#{{ activeTask?.id }} · {{ activeTask?.deviceId }}</span></el-form-item><el-form-item label="喷洒时长"><el-input-number v-model="executeForm.duration" :min="1" :max="3600" /><span class="unit">秒</span></el-form-item></el-form>
      <template #footer><el-button @click="executeVisible = false">取消</el-button><el-button type="primary" :loading="executing" @click="submitExecute">确认下发</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { CirclePlus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignSprayTask, createSprayTask, executeSprayTask, listSprayAreas, listSprayTasks, updateSprayTaskStatus } from '@/api/spray'
import { listDevice } from '@/api/ppdv/device'

const statusOptions = [{ value: 'PENDING', label: '待处理' }, { value: 'ASSIGNED', label: '已分配' }, { value: 'IN_PROGRESS', label: '执行中' }, { value: 'COMPLETED', label: '已完成' }, { value: 'FAILED', label: '失败' }]
const statusSummary = statusOptions.slice(0, 4)
const tasks = ref([]), areas = ref([]), devices = ref([]), loading = ref(false), keyword = ref(''), filterStatus = ref('')
const createVisible = ref(false), creating = ref(false), createForm = ref({ areaId: null })
const assignVisible = ref(false), assigning = ref(false), selectedDeviceId = ref('')
const executeVisible = ref(false), executing = ref(false), executeForm = ref({ duration: 30 }), activeTask = ref(null)
const filteredTasks = computed(() => { const key = keyword.value.trim().toLowerCase(); return tasks.value.filter(task => (!filterStatus.value || task.status === filterStatus.value) && (!key || `${task.deviceId || ''} ${areaName(task.areaId)}`.toLowerCase().includes(key))) })

function responseData(response) { return response.data ?? response }
function areaLabel(area) { return `${area.areaCode || ''} ${area.areaName || '未命名区域'}`.trim() }
function areaName(id) { const area = areas.value.find(item => item.id === id); return area ? areaLabel(area) : (id ? `区域 #${id}` : '未指定') }
function statusCount(status) { return tasks.value.filter(task => task.status === status).length }
function statusLabel(status) { return Object.fromEntries(statusOptions.map(item => [item.value, item.label]))[status] || status }
function statusTagType(status) { return { PENDING: 'info', ASSIGNED: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', FAILED: 'danger' }[status] || 'info' }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
async function loadTasks() { loading.value = true; try { const result = await listSprayTasks({ pageNum: 1, pageSize: 100 }); tasks.value = result.rows || [] } finally { loading.value = false } }
async function loadOptions() { const [areaResult, deviceResult] = await Promise.all([listSprayAreas(), listDevice()]); areas.value = responseData(areaResult) || []; devices.value = deviceResult.rows || [] }
function openCreate() { createForm.value = { areaId: areas.value.find(area => area.riskStatus === 'HIGH_RISK')?.id || null }; createVisible.value = true }
async function submitCreate() { if (!createForm.value.areaId) return ElMessage.warning('请选择目标区域'); creating.value = true; try { await createSprayTask(createForm.value); ElMessage.success('点喷任务已创建'); createVisible.value = false; await loadTasks() } finally { creating.value = false } }
function openAssign(task) { activeTask.value = task; selectedDeviceId.value = ''; assignVisible.value = true }
async function submitAssign() { if (!selectedDeviceId.value) return ElMessage.warning('请选择执行设备'); assigning.value = true; try { await assignSprayTask(activeTask.value.id, selectedDeviceId.value); ElMessage.success('设备已分配'); assignVisible.value = false; await loadTasks() } finally { assigning.value = false } }
function openExecute(task) { activeTask.value = task; executeForm.value = { duration: 30 }; executeVisible.value = true }
async function submitExecute() { executing.value = true; try { await executeSprayTask(activeTask.value.id, { action: 'start', duration: executeForm.value.duration, areaCode: areas.value.find(area => area.id === activeTask.value.areaId)?.areaCode }); ElMessage.success('点喷指令已下发'); executeVisible.value = false; await loadTasks() } finally { executing.value = false } }
async function setStatus(task, status) { const label = statusLabel(status); try { await ElMessageBox.confirm(`确认将任务 #${task.id} 标记为“${label}”？`, '更新任务状态', { type: status === 'FAILED' ? 'warning' : 'success' }); await updateSprayTaskStatus(task.id, status); ElMessage.success(`任务已标记为${label}`); await loadTasks(); await loadOptions() } catch (error) { if (error !== 'cancel' && error !== 'close') throw error } }
onMounted(async () => { await Promise.all([loadTasks(), loadOptions()]) })
</script>

<style scoped>
.page-heading { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:18px; }.eyebrow { color:var(--ag-primary); font-size:11px; font-weight:700; letter-spacing:1.5px; }.page-heading h1 { margin:5px 0 6px; font-size:28px; }.page-heading p { margin:0; color:var(--ag-muted); }.summary-row { margin-bottom:18px; }.summary-row :deep(.el-card__body) { padding:18px 22px; }.summary-label { color:var(--ag-muted); font-size:13px; }.summary-value { margin-top:7px; color:var(--ag-ink); font-size:32px; font-weight:700; }.summary-value.assigned { color:#c77700; }.summary-value.in_progress { color:#409eff; }.summary-value.completed { color:#76b900; }.board-card { overflow:hidden; }.toolbar { display:flex; gap:10px; padding:14px; }.toolbar .el-input { max-width:280px; }.execute-alert { margin-bottom:20px; }.unit { margin-left:8px; color:var(--ag-muted); }
</style>
