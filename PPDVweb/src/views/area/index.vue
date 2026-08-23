<template>
  <div class="area-page">
    <div class="page-heading">
      <div><div class="eyebrow">FIELD GIS</div><h1>区域管理</h1><p>维护作业地块边界、区域信息与风险状态</p></div>
      <el-button v-if="canAdd" type="primary" :icon="Plus" :disabled="!mapReady" @click="beginCreate">新增区域</el-button>
    </div>

    <div class="area-workspace">
      <el-card class="area-list" shadow="never">
        <template #header>
          <div class="list-header"><span>已建区域</span><el-button v-if="canList" link :icon="Refresh" @click="fetchAreas">刷新</el-button></div>
        </template>
        <el-input v-model="keyword" placeholder="搜索区域编号/名称" clearable :prefix-icon="Search" class="search-input" />
        <el-table v-loading="loading" :data="filteredAreas" height="520" highlight-current-row @current-change="focusArea">
          <el-table-column prop="areaCode" label="编号" width="88" />
          <el-table-column prop="areaName" label="区域名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="风险" width="76">
            <template #default="{ row }"><el-tag size="small" :type="riskType(row.riskStatus)">{{ riskLabel(row.riskStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canEdit || canRemove" label="操作" width="120">
            <template #default="{ row }"><el-button v-if="canEdit" link type="primary" size="small" @click.stop="beginEdit(row)">编辑</el-button><el-button v-if="canRemove" link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <section class="map-shell">
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-mask">{{ mapError }}</div>
        <div v-else-if="drawingBoundary" class="draw-guide">
          <div><strong>正在圈定地块</strong><span>单击添加顶点，双击完成绘制</span></div>
          <el-button size="small" @click.stop="cancelBoundaryDrawing">取消绘制</el-button>
        </div>
        <div v-else class="map-hint">点击列表可定位地块；新增或编辑时可在地图上圈定边界。</div>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑区域' : '新增区域'" width="560px" @closed="handleDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="区域编号" prop="areaCode"><el-input v-model="form.areaCode" maxlength="16" /></el-form-item>
        <el-form-item label="区域名称" prop="areaName"><el-input v-model="form.areaName" maxlength="64" /></el-form-item>
        <el-form-item label="所属大棚"><el-input v-model="form.greenhouseName" maxlength="64" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="地块边界" prop="boundary">
          <div class="boundary-row"><span>{{ form.boundary.length ? `已圈定 ${form.boundary.length} 个顶点` : '尚未圈定地块' }}</span><el-button type="primary" plain size="small" :disabled="!mapReady" @click="drawBoundary">在地图上圈地块</el-button></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="form.id && canRemove" type="danger" plain @click="handleDelete(form)">删除</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="canSave" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { addArea, deleteArea, listArea, updateArea } from '@/api/area'
import { loadAMap } from '@/utils/amap'
import { useUserStore } from '@/store/user'

const areas = ref([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const drawingBoundary = ref(false)
const startDrawingAfterClose = ref(false)
const formRef = ref()
const mapEl = ref()
const mapReady = ref(false)
const mapError = ref('')
const userStore = useUserStore()
const form = reactive({ id: null, areaCode: '', areaName: '', greenhouseName: '', arucoId: null, remark: '', boundary: [], coordinateSystem: 'GCJ-02' })
const rules = {
  areaCode: [{ required: true, message: '请输入区域编号', trigger: 'blur' }],
  areaName: [{ required: true, message: '请输入区域名称', trigger: 'blur' }],
  boundary: [{ validator: (_, value, callback) => value.length >= 3 ? callback() : callback(new Error('请在地图上圈定至少 3 个顶点')), trigger: 'change' }]
}

let AMapRef = null
let map = null
let mouseTool = null
let draftPolygon = null
let polygons = []

const canList = computed(() => userStore.hasPermission('ppdv:area:list'))
const canAdd = computed(() => userStore.hasPermission('ppdv:area:add'))
const canEdit = computed(() => userStore.hasPermission('ppdv:area:edit'))
const canRemove = computed(() => userStore.hasPermission('ppdv:area:remove'))
const canSave = computed(() => form.id ? canEdit.value : canAdd.value)

const filteredAreas = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return text ? areas.value.filter(area => `${area.areaCode} ${area.areaName}`.toLowerCase().includes(text)) : areas.value
})

function riskType(status) { return { NORMAL: 'success', SUSPICIOUS: 'warning', HIGH_RISK: 'danger', SPRAYED_PENDING_REVIEW: 'info' }[status] || 'info' }
function riskLabel(status) { return { NORMAL: '正常', SUSPICIOUS: '关注', HIGH_RISK: '高风险', SPRAYED_PENDING_REVIEW: '待复查' }[status] || status || '未知' }
function parseBoundary(area) {
  try {
    const boundary = JSON.parse(area.boundaryJson || '[]')
    return Array.isArray(boundary) && boundary.length >= 3 ? boundary : []
  } catch (_) { return [] }
}
function pathToArray(path) { return path.map(point => [point.getLng(), point.getLat()]) }
function polygonOptions(area) {
  const colors = { NORMAL: '#76b900', SUSPICIOUS: '#c77700', HIGH_RISK: '#d93025' }
  const color = colors[area.riskStatus] || '#76b900'
  return { path: parseBoundary(area), strokeColor: color, strokeWeight: 3, fillColor: color, fillOpacity: 0.2, extData: area }
}
function renderPolygons() {
  if (!map || !AMapRef) return
  if (polygons.length) map.remove(polygons)
  polygons = areas.value.filter(area => parseBoundary(area).length).map(area => {
    const polygon = new AMapRef.Polygon(polygonOptions(area))
    polygon.on('click', () => canEdit.value ? beginEdit(area) : focusArea(area))
    return polygon
  })
  if (polygons.length) map.add(polygons)
}
function setDraft(boundary) {
  if (!map || !AMapRef) return
  if (draftPolygon) map.remove(draftPolygon)
  draftPolygon = new AMapRef.Polygon({ path: boundary, strokeColor: '#409eff', strokeWeight: 4, fillColor: '#409eff', fillOpacity: 0.2 })
  map.add(draftPolygon)
  form.boundary = boundary
  formRef.value?.validateField('boundary').catch(() => {})
  map.setFitView([draftPolygon])
}
function clearDraft() {
  if (draftPolygon && map) map.remove(draftPolygon)
  draftPolygon = null
}
function closeMouseTool(clear = true) {
  mouseTool?.close(clear)
}

async function fetchAreas() {
  if (!canList.value) {
    areas.value = []
    renderPolygons()
    return
  }
  loading.value = true
  try {
    const res = await listArea()
    areas.value = res.data || []
    renderPolygons()
  } finally { loading.value = false }
}
async function initMap() {
  try {
    AMapRef = await loadAMap()
    map = new AMapRef.Map(mapEl.value, { zoom: 16, center: [120.65, 30.12], mapStyle: 'amap://styles/darkblue' })
    mouseTool = new AMapRef.MouseTool(map)
    mouseTool.on('draw', event => {
      const drawnOverlay = event.obj
      const boundary = drawnOverlay?.getPath ? pathToArray(drawnOverlay.getPath()) : []
      if (drawnOverlay && map) map.remove(drawnOverlay)
      closeMouseTool(true)
      if (!drawingBoundary.value) return
      drawingBoundary.value = false
      if (boundary.length >= 3) setDraft(boundary)
      dialogVisible.value = true
    })
    mapReady.value = true
    renderPolygons()
  } catch (error) { mapError.value = `${error.message}（填好 Key 后可使用 GIS 地图）` }
}
function resetForm() {
  Object.assign(form, { id: null, areaCode: '', areaName: '', greenhouseName: '', arucoId: null, remark: '', boundary: [], coordinateSystem: 'GCJ-02' })
  clearDraft()
}
function beginCreate() {
  if (!canAdd.value) return
  resetForm()
  dialogVisible.value = true
}
function beginEdit(area) {
  if (!canEdit.value) return
  Object.assign(form, {
    id: area.id,
    areaCode: area.areaCode,
    areaName: area.areaName,
    greenhouseName: area.greenhouseName || '',
    arucoId: area.arucoId ?? null,
    remark: area.remark || '',
    boundary: parseBoundary(area),
    coordinateSystem: area.coordinateSystem || 'GCJ-02'
  })
  dialogVisible.value = true
  if (form.boundary.length) setDraft(form.boundary)
}
function focusArea(area) {
  const polygon = polygons.find(item => item.getExtData()?.id === area?.id)
  if (polygon && map) map.setFitView([polygon])
}
function drawBoundary() {
  if (!mapReady.value || !mouseTool) return
  startDrawingAfterClose.value = true
  dialogVisible.value = false
}
function handleDialogClosed() {
  closeMouseTool(true)
  if (startDrawingAfterClose.value) {
    startDrawingAfterClose.value = false
    drawingBoundary.value = true
    mouseTool?.polygon({ strokeColor: '#409eff', fillColor: '#409eff', fillOpacity: 0.2 })
    return
  }
  drawingBoundary.value = false
  clearDraft()
}
function cancelBoundaryDrawing() {
  closeMouseTool(true)
  drawingBoundary.value = false
  dialogVisible.value = true
}
async function submit() {
  if (!canSave.value) return
  await formRef.value.validate()
  saving.value = true
  const payload = {
    id: form.id,
    areaCode: form.areaCode.trim(),
    areaName: form.areaName.trim(),
    greenhouseName: form.greenhouseName.trim(),
    arucoId: form.arucoId,
    remark: form.remark.trim(),
    boundaryJson: JSON.stringify(form.boundary),
    coordinateSystem: form.coordinateSystem
  }
  try {
    if (form.id) await updateArea(payload)
    else await addArea(payload)
    ElMessage.success('区域已保存')
    dialogVisible.value = false
    await fetchAreas()
  } finally { saving.value = false }
}
async function handleDelete(area) {
  if (!canRemove.value || !area?.id) return
  try {
    await ElMessageBox.confirm(`确定删除区域“${area.areaName}”吗？有关联业务数据的区域会被后端拒绝删除。`, '删除确认', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  await deleteArea(area.id)
  ElMessage.success('区域已删除')
  if (form.id === area.id) dialogVisible.value = false
  await fetchAreas()
}

onMounted(async () => { await fetchAreas(); await initMap() })
onBeforeUnmount(() => {
  closeMouseTool(true)
  clearDraft()
  if (map) map.destroy()
})
</script>

<style scoped>
.area-page { min-height: 100%; display: flex; flex-direction: column; }
.area-page .page-heading { display: flex; align-items: center; justify-content: space-between; }
.area-workspace { min-height: 590px; flex: 1; display: grid; grid-template-columns: 390px 1fr; gap: 14px; }
.area-list { min-width: 0; }
.list-header { display: flex; align-items: center; justify-content: space-between; font-weight: 650; }
.search-input { margin-bottom: 12px; }
.map-shell { position: relative; overflow: hidden; border: 1px solid #232a22; border-radius: var(--ag-radius); background: #171c17; }
.map { position: absolute; inset: 0; }
.map-mask { position: absolute; inset: 0; display: grid; place-items: center; padding: 30px; color: #899487; text-align: center; }
.map-hint { position: absolute; z-index: 1; left: 12px; bottom: 12px; padding: 7px 10px; color: #c1c9bd; background: rgb(21 27 21 / 92%); border: 1px solid #3a4438; border-radius: 2px; font-size: 11px; }
.draw-guide { position: absolute; z-index: 2; top: 12px; left: 50%; display: flex; align-items: center; gap: 18px; min-width: 360px; padding: 10px 12px; color: #e6eae4; background: rgb(21 27 21 / 96%); border: 1px solid #409eff; border-radius: 3px; transform: translateX(-50%); box-shadow: 0 6px 18px rgb(0 0 0 / 28%); }
.draw-guide div { display: flex; flex-direction: column; gap: 2px; }
.draw-guide span { color: #aeb8ac; font-size: 11px; }
.boundary-row { display: flex; align-items: center; justify-content: space-between; width: 100%; color: var(--ag-muted); }
@media (max-width: 1120px) { .area-workspace { grid-template-columns: 1fr; } .area-list { min-height: 360px; } }
</style>
