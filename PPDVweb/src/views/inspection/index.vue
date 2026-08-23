<template>
  <div class="inspection-page">
    <div class="page-heading">
      <div><div class="eyebrow">FIELD INSPECTION</div><h1>巡检管理</h1><p>启动区域巡检，归集采集图片与识别结果</p></div>
      <el-button v-if="canManageInspection" type="primary" :icon="CirclePlus" @click="openStartDialog">开始巡检</el-button>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="8"><el-card><div class="stat-num">{{ totalRecords }}</div><div class="stat-label">巡检记录</div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat-num running">{{ inProgressCount }}</div><div class="stat-label">本页进行中</div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat-num warning">{{ anomalyTotal }}</div><div class="stat-label">本页已识别异常</div></el-card></el-col>
    </el-row>

    <section class="surface-card table-shell">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="筛选当前页设备编号或区域" :prefix-icon="Search" /><el-button :icon="Refresh" @click="loadRecords">刷新</el-button></div>
      <el-table v-loading="loading" :data="filteredRecords" @row-click="openDetail">
        <el-table-column prop="id" label="记录号" width="90" />
        <el-table-column prop="deviceId" label="巡检设备" min-width="130" />
        <el-table-column label="巡检区域" min-width="150"><template #default="{ row }">{{ areaName(row.areaId) }}</template></el-table-column>
        <el-table-column prop="startTime" label="开始时间" min-width="170"><template #default="{ row }">{{ formatTime(row.startTime) }}</template></el-table-column>
        <el-table-column label="图片 / 异常" width="120"><template #default="{ row }">{{ row.imageCount || 0 }} / {{ row.anomalyCount || 0 }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'">{{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="190"><template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">查看</el-button><el-button v-if="canManageInspection && row.status !== 'COMPLETED'" link type="success" @click.stop="finishInspection(row)">完成巡检</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="totalRecords"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadRecords"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="startVisible" title="开始巡检" width="460px" destroy-on-close>
      <el-alert v-if="!canListAreas" title="当前账号没有区域列表权限，可在不指定区域的情况下开始巡检。" type="info" :closable="false" show-icon class="option-alert" />
      <el-form :model="startForm" label-width="88px"><el-form-item label="巡检设备" required><el-select v-model="startForm.deviceId" filterable placeholder="选择在线设备"><el-option v-for="device in devices" :key="device.deviceId" :label="`${device.name || device.deviceId}（${device.deviceId}）`" :value="device.deviceId" /></el-select></el-form-item><el-form-item label="巡检区域"><el-select v-if="canListAreas" v-model="startForm.areaId" clearable placeholder="可选"><el-option v-for="area in areas" :key="area.id" :label="areaLabel(area)" :value="area.id" /></el-select><span v-else class="muted">区域列表不可用，本次不指定区域</span></el-form-item></el-form>
      <template #footer><el-button @click="startVisible = false">取消</el-button><el-button type="primary" :loading="starting" @click="submitStart">开始</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="`巡检记录 #${selectedRecord?.id || ''}`" size="680px" destroy-on-close>
      <div v-if="selectedRecord" v-loading="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="detail-alert" />
        <el-descriptions :column="2" border><el-descriptions-item label="巡检设备">{{ selectedRecord.deviceId }}</el-descriptions-item><el-descriptions-item label="巡检区域">{{ areaName(selectedRecord.areaId) }}</el-descriptions-item><el-descriptions-item label="开始时间">{{ formatTime(selectedRecord.startTime) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ formatTime(selectedRecord.endTime) }}</el-descriptions-item></el-descriptions>
        <div class="section-title"><span>采集图片</span><el-upload v-if="canManageInspection" :show-file-list="false" accept="image/*" :http-request="handleUpload"><el-button type="primary" plain :icon="Upload">上传图片</el-button></el-upload></div>
        <div v-if="images.length" class="image-grid"><article v-for="image in images" :key="image.id" class="image-card" :class="{ selected: selectedImage?.id === image.id }" @click="selectImage(image)"><el-image :src="inspectionImageUrl(image.filePath)" fit="cover"><template #error><div class="image-fallback"><el-icon><Picture /></el-icon><span>图片不可访问</span></div></template></el-image><div>图片 #{{ image.id }}</div><small>{{ formatTime(image.capturedAt) }}</small></article></div>
        <el-empty v-else description="尚未上传巡检图片" :image-size="80" />
        <div class="section-title"><span>识别结果{{ selectedImage ? `（图片 #${selectedImage.id}）` : '' }}</span><el-button v-if="canManageInspection" type="primary" plain :disabled="!selectedImage" @click="openDetectionDialog">录入结果</el-button></div>
        <el-table v-loading="detectionsLoading" :data="detections" size="small"><el-table-column prop="category" label="识别类别" /><el-table-column label="置信度" width="100"><template #default="{ row }">{{ percent(row.confidence) }}</template></el-table-column><el-table-column label="处理状态" width="110"><template #default="{ row }"><el-tag size="small" :type="row.status === 'CONFIRMED' ? 'success' : 'warning'">{{ detectionStatus(row.status) }}</el-tag></template></el-table-column></el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="detectionVisible" title="录入识别结果" width="470px" destroy-on-close>
      <el-form :model="detectionForm" label-width="88px"><el-form-item label="识别类别" required><el-select v-model="detectionForm.category"><el-option v-for="item in categories" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="置信度" required><el-input-number v-model="detectionForm.confidence" :min="0" :max="1" :step="0.01" :precision="2" /></el-form-item><el-form-item label="处理状态"><el-select v-model="detectionForm.status"><el-option label="待确认" value="PENDING" /><el-option label="已确认" value="CONFIRMED" /><el-option label="已忽略" value="IGNORED" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="detectionVisible = false">取消</el-button><el-button type="primary" :loading="savingDetection" @click="submitDetection">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { CirclePlus, Picture, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addDetection, completeInspection, getInspection, listAreas, listImageDetections, listInspection, startInspection, uploadInspectionImage } from '@/api/inspection'
import { listDevice } from '@/api/ppdv/device'
import { useUserStore } from '@/store/user'
import { resolveApiAssetUrl } from '@/utils/apiAssetUrl'

const userStore = useUserStore()
const loading = ref(false), records = ref([]), areas = ref([]), devices = ref([]), keyword = ref('')
const pageNum = ref(1), pageSize = ref(20), totalRecords = ref(0)
const startVisible = ref(false), starting = ref(false), startForm = ref({ deviceId: '', areaId: null })
const detailVisible = ref(false), detailLoading = ref(false), detailError = ref(''), selectedRecord = ref(null), images = ref([]), selectedImage = ref(null), detections = ref([]), detectionsLoading = ref(false)
const detectionVisible = ref(false), savingDetection = ref(false), detectionForm = ref({ category: '病斑', confidence: 0.8, status: 'PENDING' })
const categories = ['病斑', '虫害', '黄化', '萎蔫', '花朵', '幼果', '成熟果']
const canManageInspection = computed(() => userStore.hasPermission('ppdv:inspection:upload'))
const canListAreas = computed(() => userStore.hasPermission('ppdv:area:list'))
const canListDevices = computed(() => userStore.hasPermission('ppdv:device:list'))
const filteredRecords = computed(() => { const value = keyword.value.trim().toLowerCase(); return value ? records.value.filter(row => `${row.deviceId} ${areaName(row.areaId)}`.toLowerCase().includes(value)) : records.value })
const inProgressCount = computed(() => records.value.filter(row => row.status !== 'COMPLETED').length)
const anomalyTotal = computed(() => records.value.reduce((total, row) => total + (row.anomalyCount || 0), 0))

let recordRequestId = 0
let detailRequestId = 0
let detectionRequestId = 0

function responseData(response) { return response.data ?? response }
function areaLabel(area) { return `${area.areaCode || ''} ${area.areaName || '未命名区域'}`.trim() }
function areaName(id) { const area = areas.value.find(item => item.id === id); return area ? areaLabel(area) : (id ? `区域 #${id}` : '未指定') }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function percent(value) { return value == null ? '-' : `${Math.round(value * 100)}%` }
function detectionStatus(value) { return { PENDING: '待确认', CONFIRMED: '已确认', IGNORED: '已忽略' }[value] || value || '待确认' }
function inspectionImageUrl(filePath) { return resolveApiAssetUrl(filePath) }

async function loadRecords() {
  const requestId = ++recordRequestId
  loading.value = true
  try {
    const result = await listInspection({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (requestId !== recordRequestId) return
    records.value = result.rows || []
    totalRecords.value = Number(result.total) || 0
  } finally {
    if (requestId === recordRequestId) loading.value = false
  }
}
async function loadOptions() {
  const tasks = []
  if (canListDevices.value) {
    tasks.push(listDevice({ pageNum: 1, pageSize: 200, orderByColumn: 'deviceId', isAsc: 'asc' })
      .then(result => { devices.value = (result.rows || []).filter(device => device.status === 'ONLINE') })
      .catch(() => { devices.value = [] }))
  } else {
    devices.value = []
  }
  if (canListAreas.value) {
    tasks.push(listAreas().then(result => { areas.value = responseData(result) || [] }).catch(() => { areas.value = [] }))
  } else {
    areas.value = []
  }
  await Promise.allSettled(tasks)
}
function handlePageSizeChange() { pageNum.value = 1; loadRecords() }
function openStartDialog() {
  if (!devices.value.length) return ElMessage.warning('没有在线可用设备或当前账号缺少设备列表权限')
  startForm.value = { deviceId: devices.value[0]?.deviceId || '', areaId: null }
  startVisible.value = true
}
async function submitStart() { if (!startForm.value.deviceId) return ElMessage.warning('请选择巡检设备'); starting.value = true; try { await startInspection(startForm.value); ElMessage.success('巡检已开始'); startVisible.value = false; pageNum.value = 1; await loadRecords() } finally { starting.value = false } }
async function finishInspection(row) { try { await ElMessageBox.confirm('确认结束该巡检记录？', '完成巡检', { type: 'warning' }); await completeInspection(row.id); ElMessage.success('巡检已完成'); await loadRecords(); if (selectedRecord.value?.id === row.id) await openDetail(row) } catch (error) { if (error === 'cancel' || error === 'close') return } }
async function openDetail(row) {
  const requestId = ++detailRequestId
  detectionRequestId++
  selectedRecord.value = row
  images.value = []
  selectedImage.value = null
  detections.value = []
  detectionsLoading.value = false
  detailError.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const result = await getInspection(row.id)
    if (requestId !== detailRequestId) return
    const data = responseData(result)
    selectedRecord.value = data.record || row
    images.value = data.images || []
  } catch (error) {
    if (requestId === detailRequestId) detailError.value = error?.message || '巡检详情加载失败'
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}
async function selectImage(image) {
  const imageId = Number(image?.id)
  if (!Number.isSafeInteger(imageId) || imageId <= 0) return ElMessage.warning('图片 ID 无效，无法查询识别结果')
  const requestId = ++detectionRequestId
  selectedImage.value = image
  detections.value = []
  detectionsLoading.value = true
  try {
    const result = await listImageDetections(imageId)
    if (requestId === detectionRequestId && selectedImage.value?.id === image.id) detections.value = responseData(result) || []
  } finally {
    if (requestId === detectionRequestId) detectionsLoading.value = false
  }
}
async function handleUpload(request) { const record = selectedRecord.value; if (!record?.id) throw new Error('请先选择巡检记录'); const data = new FormData(); data.append('file', request.file); data.append('inspectionId', record.id); if (record.areaId) data.append('areaId', record.areaId); if (record.deviceId) data.append('deviceId', record.deviceId); await uploadInspectionImage(data); ElMessage.success('图片上传成功'); await openDetail(record) }
function openDetectionDialog() { detectionForm.value = { category: '病斑', confidence: 0.8, status: 'PENDING' }; detectionVisible.value = true }
async function submitDetection() { const image = selectedImage.value; const imageId = Number(image?.id); if (!Number.isSafeInteger(imageId) || imageId <= 0) return ElMessage.warning('请选择有效的巡检图片'); savingDetection.value = true; try { await addDetection({ ...detectionForm.value, imageId, areaId: selectedRecord.value.areaId }); ElMessage.success('识别结果已保存'); detectionVisible.value = false; await selectImage(image); await loadRecords() } finally { savingDetection.value = false } }
onMounted(() => { loadRecords(); loadOptions() })
</script>

<style scoped>
.page-heading { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:18px; }.eyebrow { color:var(--ag-primary); font-size:11px; font-weight:700; letter-spacing:1.5px; }.page-heading h1 { margin:5px 0 6px; font-size:28px; }.page-heading p { margin:0; color:var(--ag-muted); }.stat-row { margin-bottom:18px; }.stat-row :deep(.el-card__body) { padding:19px 22px; }.stat-num { color:var(--ag-ink); font-size:32px; font-weight:700; }.stat-num.running { color:#c77700; }.stat-num.warning { color:#d93025; }.stat-label { margin-top:6px; color:var(--ag-muted); font-size:13px; }.table-shell { overflow:hidden; }.toolbar { display:flex; gap:10px; padding:14px; }.toolbar .el-input { max-width:280px; }.pagination-bar { display:flex; justify-content:center; padding:14px; }.option-alert, .detail-alert { margin-bottom:16px; }.muted { color:var(--ag-muted); font-size:12px; }.section-title { display:flex; align-items:center; justify-content:space-between; margin:26px 0 12px; font-size:16px; font-weight:650; }.image-grid { display:grid; grid-template-columns:repeat(3, 1fr); gap:12px; }.image-card { overflow:hidden; padding:6px; border:1px solid var(--ag-border); border-radius:4px; cursor:pointer; }.image-card.selected { border-color:var(--ag-primary); box-shadow:0 0 0 1px var(--ag-primary); }.image-card :deep(.el-image) { display:block; width:100%; height:112px; margin-bottom:6px; background:#f4f5f2; }.image-card div:not(.image-fallback) { font-size:12px; }.image-card small { color:var(--ag-muted); font-size:10px; }.image-fallback { height:100%; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:6px; color:var(--ag-muted); font-size:11px; }.image-fallback .el-icon { font-size:24px; }
</style>
