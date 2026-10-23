<template>
  <div class="app-container area-page">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">FIELD GIS</div><h1>区域管理</h1><p>维护作业地块边界、区域信息与风险状态</p></div>
      <el-button type="primary" icon="Plus" :disabled="!mapReady" v-hasPermi="['ppdv:area:add']" @click="beginCreate">新增区域</el-button>
    </div>

    <div class="area-workspace">
      <el-card class="area-list" shadow="never">
        <template #header>
          <div class="list-header"><span>已建区域</span><el-button link icon="Refresh" @click="getList">刷新</el-button></div>
        </template>
        <el-input v-model="keyword" placeholder="搜索区域编号/名称" clearable class="search-input" />
        <el-table v-loading="loading" :data="filteredAreas" height="520" highlight-current-row @current-change="focusArea">
          <el-table-column prop="areaCode" label="编号" width="88" />
          <el-table-column prop="areaName" label="区域名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="风险" width="76">
            <template #default="scope">
              <el-tag size="small" :type="riskTagType(scope.row.riskStatus)">{{ riskLabel(scope.row.riskStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="scope">
              <el-button link type="primary" size="small" v-hasPermi="['ppdv:area:edit']" @click.stop="beginEdit(scope.row)">编辑</el-button>
              <el-button link type="danger" size="small" v-hasPermi="['ppdv:area:remove']" @click.stop="handleDelete(scope.row)">删除</el-button>
            </template>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑区域' : '新增区域'" width="560px" append-to-body @closed="handleDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="区域编号" prop="areaCode">
          <el-input v-model="form.areaCode" maxlength="16" />
        </el-form-item>
        <el-form-item label="区域名称" prop="areaName">
          <el-input v-model="form.areaName" maxlength="64" />
        </el-form-item>
        <el-form-item label="所属大棚">
          <el-input v-model="form.greenhouseName" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="地块边界" prop="boundary">
          <div class="boundary-row">
            <span>{{ form.boundary.length ? `已圈定 ${form.boundary.length} 个顶点` : '尚未圈定地块' }}</span>
            <el-button type="primary" plain size="small" :disabled="!mapReady" @click="drawBoundary">在地图上圈地块</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="saving" @click="submitForm">保 存</el-button>
          <el-button @click="dialogVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Area">
import { checkPermi } from "@/utils/permission"
import { listArea, addArea, updateArea, delArea } from "@/api/ppdv/area"
import { loadAMap } from "@/utils/amap"

const { proxy } = getCurrentInstance()

const areaList = ref([])
const keyword = ref("")
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const drawingBoundary = ref(false)
const startDrawingAfterClose = ref(false)
const mapEl = ref(null)
const mapReady = ref(false)
const mapError = ref("")

const data = reactive({
  form: {
    id: null,
    areaCode: "",
    areaName: "",
    greenhouseName: "",
    arucoId: null,
    remark: "",
    boundary: [],
    coordinateSystem: "GCJ-02"
  },
  rules: {
    areaCode: [{ required: true, message: "区域编号不能为空", trigger: "blur" }],
    areaName: [{ required: true, message: "区域名称不能为空", trigger: "blur" }],
    boundary: [{ validator: (rule, value, callback) => value.length >= 3 ? callback() : callback(new Error("请在地图上圈定至少 3 个顶点")), trigger: "change" }]
  }
})
const { form, rules } = toRefs(data)

let AMapRef = null
let map = null
let mouseTool = null
let draftPolygon = null
let polygons = []

const filteredAreas = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return text ? areaList.value.filter(area => `${area.areaCode} ${area.areaName}`.toLowerCase().includes(text)) : areaList.value
})

function riskTagType(status) {
  return { NORMAL: "success", SUSPICIOUS: "warning", HIGH_RISK: "danger", SPRAYED_PENDING_REVIEW: "info" }[status] || "info"
}
function riskLabel(status) {
  return { NORMAL: "正常", SUSPICIOUS: "关注", HIGH_RISK: "高风险", SPRAYED_PENDING_REVIEW: "待复查" }[status] || status || "未知"
}
function parseBoundary(area) {
  try {
    const boundary = JSON.parse(area.boundaryJson || "[]")
    return Array.isArray(boundary) && boundary.length >= 3 ? boundary : []
  } catch (e) {
    return []
  }
}
function pathToArray(path) {
  return path.map(point => [point.getLng(), point.getLat()])
}
function polygonOptions(area) {
  const colors = { NORMAL: "#76b900", SUSPICIOUS: "#c77700", HIGH_RISK: "#d93025" }
  const color = colors[area.riskStatus] || "#76b900"
  return { path: parseBoundary(area), strokeColor: color, strokeWeight: 3, fillColor: color, fillOpacity: 0.2, extData: area }
}
function renderPolygons() {
  if (!map || !AMapRef) return
  if (polygons.length) map.remove(polygons)
  polygons = areaList.value.filter(area => parseBoundary(area).length).map(area => {
    const polygon = new AMapRef.Polygon(polygonOptions(area))
    polygon.on("click", () => checkPermi(["ppdv:area:edit"]) ? beginEdit(area) : focusArea(area))
    return polygon
  })
  if (polygons.length) map.add(polygons)
}
function setDraft(boundary) {
  if (!map || !AMapRef) return
  if (draftPolygon) map.remove(draftPolygon)
  draftPolygon = new AMapRef.Polygon({ path: boundary, strokeColor: "#409eff", strokeWeight: 4, fillColor: "#409eff", fillOpacity: 0.2 })
  map.add(draftPolygon)
  form.value.boundary = boundary
  proxy.$refs["formRef"]?.validateField("boundary").catch(() => {})
  map.setFitView([draftPolygon])
}
function clearDraft() {
  if (draftPolygon && map) map.remove(draftPolygon)
  draftPolygon = null
}
function closeMouseTool(clear = true) {
  mouseTool?.close(clear)
}

function getList() {
  loading.value = true
  listArea().then(response => {
    areaList.value = response.data || []
    renderPolygons()
  }).finally(() => {
    loading.value = false
  })
}
function initMap() {
  loadAMap().then(AMap => {
    AMapRef = AMap
    map = new AMap.Map(mapEl.value, { zoom: 16, center: [120.65, 30.12], mapStyle: "amap://styles/darkblue" })
    mouseTool = new AMap.MouseTool(map)
    mouseTool.on("draw", event => {
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
  }).catch(error => {
    mapError.value = error?.message || String(error)
  })
}
function reset() {
  Object.assign(form.value, { id: null, areaCode: "", areaName: "", greenhouseName: "", arucoId: null, remark: "", boundary: [], coordinateSystem: "GCJ-02" })
  clearDraft()
}
function beginCreate() {
  reset()
  dialogVisible.value = true
}
function beginEdit(area) {
  Object.assign(form.value, {
    id: area.id,
    areaCode: area.areaCode,
    areaName: area.areaName,
    greenhouseName: area.greenhouseName || "",
    arucoId: area.arucoId ?? null,
    remark: area.remark || "",
    boundary: parseBoundary(area),
    coordinateSystem: area.coordinateSystem || "GCJ-02"
  })
  dialogVisible.value = true
  if (form.value.boundary.length) setDraft(form.value.boundary)
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
    mouseTool?.polygon({ strokeColor: "#409eff", fillColor: "#409eff", fillOpacity: 0.2 })
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
function submitForm() {
  proxy.$refs["formRef"].validate(valid => {
    if (!valid) return
    saving.value = true
    const payload = {
      id: form.value.id,
      areaCode: form.value.areaCode.trim(),
      areaName: form.value.areaName.trim(),
      greenhouseName: form.value.greenhouseName.trim(),
      arucoId: form.value.arucoId,
      remark: form.value.remark.trim(),
      boundaryJson: JSON.stringify(form.value.boundary),
      coordinateSystem: form.value.coordinateSystem
    }
    const request = form.value.id ? updateArea(payload) : addArea(payload)
    request.then(() => {
      proxy.$modal.msgSuccess("区域已保存")
      dialogVisible.value = false
      getList()
    }).finally(() => {
      saving.value = false
    })
  })
}
function handleDelete(area) {
  if (!area?.id) return
  proxy.$modal.confirm(`确定删除区域“${area.areaName}”吗？有关联业务数据的区域会被后端拒绝删除。`).then(() => {
    return delArea(area.id)
  }).then(() => {
    proxy.$modal.msgSuccess("区域已删除")
    if (form.value.id === area.id) dialogVisible.value = false
    getList()
  }).catch(() => {})
}

onMounted(() => {
  getList()
  initMap()
})
onBeforeUnmount(() => {
  closeMouseTool(true)
  clearDraft()
  if (map) map.destroy()
})
</script>

<style scoped>
.area-workspace { min-height: 590px; display: grid; grid-template-columns: 390px 1fr; gap: 14px; }
.area-list { min-width: 0; }
.list-header { display: flex; align-items: center; justify-content: space-between; font-weight: 650; }
.search-input { margin-bottom: 12px; }
.map-shell { position: relative; overflow: hidden; border: 1px solid #232a22; border-radius: 4px; background: #171c17; }
.map { position: absolute; inset: 0; }
.map-mask { position: absolute; inset: 0; display: grid; place-items: center; padding: 30px; color: #899487; text-align: center; background: #171c17; }
.map-hint { position: absolute; z-index: 1; left: 12px; bottom: 12px; padding: 7px 10px; color: #c1c9bd; background: rgb(21 27 21 / 92%); border: 1px solid #3a4438; border-radius: 2px; font-size: 11px; }
.draw-guide { position: absolute; z-index: 2; top: 12px; left: 50%; display: flex; align-items: center; gap: 18px; min-width: 360px; padding: 10px 12px; color: #e6eae4; background: rgb(21 27 21 / 96%); border: 1px solid #409eff; border-radius: 3px; transform: translateX(-50%); box-shadow: 0 6px 18px rgb(0 0 0 / 28%); }
.draw-guide div { display: flex; flex-direction: column; gap: 2px; }
.draw-guide span { color: #aeb8ac; font-size: 11px; }
.boundary-row { display: flex; align-items: center; justify-content: space-between; width: 100%; color: var(--ag-muted); }
@media (max-width: 1120px) { .area-workspace { grid-template-columns: 1fr; } .area-list { min-height: 360px; } }
</style>
