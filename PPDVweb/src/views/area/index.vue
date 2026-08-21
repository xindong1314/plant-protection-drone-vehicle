<template>
  <div class="area-page">
    <div class="page-heading">
      <div><div class="eyebrow">FIELD GIS</div><h1>区域管理</h1><p>维护作业地块边界、区域信息与风险状态</p></div>
      <el-button type="primary" :icon="Plus" :disabled="!mapReady" @click="beginCreate">新增区域</el-button>
    </div>

    <div class="area-workspace">
      <el-card class="area-list" shadow="never">
        <template #header>
          <div class="list-header"><span>已建区域</span><el-button link :icon="Refresh" @click="fetchAreas">刷新</el-button></div>
        </template>
        <el-input v-model="keyword" placeholder="搜索区域编号/名称" clearable :prefix-icon="Search" class="search-input" />
        <el-table v-loading="loading" :data="filteredAreas" height="520" highlight-current-row @current-change="focusArea">
          <el-table-column prop="areaCode" label="编号" width="88" />
          <el-table-column prop="areaName" label="区域名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="风险" width="76">
            <template #default="{ row }"><el-tag size="small" :type="riskType(row.riskStatus)">{{ riskLabel(row.riskStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button link type="primary" size="small" @click.stop="beginEdit(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>

      <section class="map-shell">
        <div ref="mapEl" class="map"></div>
        <div v-if="mapError" class="map-mask">{{ mapError }}</div>
        <div v-else class="map-hint">点击列表可定位地块；新增或编辑时可在地图上圈定边界。</div>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑区域' : '新增区域'" width="560px" @closed="clearDraft">
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
        <el-button v-if="form.id" type="danger" plain @click="handleDelete">删除</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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

const areas = ref([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()
const mapEl = ref()
const mapReady = ref(false)
const mapError = ref('')
const form = reactive({ id: null, areaCode: '', areaName: '', greenhouseName: '', remark: '', boundary: [] })
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

const filteredAreas = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return text ? areas.value.filter(area => `${area.areaCode} ${area.areaName}`.toLowerCase().includes(text)) : areas.value
})

function riskType(status) { return { NORMAL: 'success', SUSPICIOUS: 'warning', HIGH_RISK: 'danger' }[status] || 'info' }
function riskLabel(status) { return { NORMAL: '正常', SUSPICIOUS: '关注', HIGH_RISK: '高风险' }[status] || status || '未知' }
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
    polygon.on('click', () => beginEdit(area))
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

async function fetchAreas() {
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
      if (!dialogVisible.value || !event.obj?.getPath) return
      setDraft(pathToArray(event.obj.getPath()))
      map.remove(event.obj)
      mouseTool.close(false)
    })
    mapReady.value = true
    renderPolygons()
  } catch (error) { mapError.value = `${error.message}（填好 Key 后可使用 GIS 地图）` }
}
function resetForm() {
  Object.assign(form, { id: null, areaCode: '', areaName: '', greenhouseName: '', remark: '', boundary: [] })
  clearDraft()
}
function beginCreate() { resetForm(); dialogVisible.value = true }
function beginEdit(area) {
  Object.assign(form, { id: area.id, areaCode: area.areaCode, areaName: area.areaName, greenhouseName: area.greenhouseName || '', remark: area.remark || '', boundary: parseBoundary(area) })
  dialogVisible.value = true
  if (form.boundary.length) setDraft(form.boundary)
}
function focusArea(area) {
  const polygon = polygons.find(item => item.getExtData()?.id === area?.id)
  if (polygon && map) map.setFitView([polygon])
}
function drawBoundary() { mouseTool?.polygon({ strokeColor: '#409eff', fillColor: '#409eff', fillOpacity: 0.2 }) }
async function submit() {
  await formRef.value.validate()
  saving.value = true
  const payload = { id: form.id, areaCode: form.areaCode.trim(), areaName: form.areaName.trim(), greenhouseName: form.greenhouseName.trim(), remark: form.remark.trim(), boundaryJson: JSON.stringify(form.boundary), coordinateSystem: 'GCJ-02' }
  try {
    if (form.id) await updateArea(payload)
    else await addArea(payload)
    ElMessage.success('区域已保存')
    dialogVisible.value = false
    await fetchAreas()
  } finally { saving.value = false }
}
async function handleDelete() {
  await ElMessageBox.confirm(`确定删除区域“${form.areaName}”吗？`, '删除确认', { type: 'warning' })
  await deleteArea(form.id)
  ElMessage.success('区域已删除')
  dialogVisible.value = false
  await fetchAreas()
}

onMounted(async () => { await fetchAreas(); await initMap() })
onBeforeUnmount(() => { if (map) map.destroy() })
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
.boundary-row { display: flex; align-items: center; justify-content: space-between; width: 100%; color: var(--ag-muted); }
@media (max-width: 1120px) { .area-workspace { grid-template-columns: 1fr; } .area-list { min-height: 360px; } }
</style>
