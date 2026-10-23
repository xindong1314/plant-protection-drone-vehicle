<template>
  <div class="app-container">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">FIELD INSPECTION</div><h1>巡检记录</h1><p>查看区域巡检记录、采集图片与识别结果</p></div>
      <span class="admin-live">只读数据</span>
    </div>

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="设备编号或区域名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8 action-bar">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="inspectionList">
      <el-table-column label="记录号" align="center" prop="id" width="90" />
      <el-table-column label="巡检设备" align="center" prop="deviceId" min-width="130" />
      <el-table-column label="巡检区域" align="center" min-width="150">
        <template #default="scope">
          <span>{{ areaName(scope.row.areaId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" align="center" prop="startTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.startTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结束时间" align="center" prop="endTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.endTime) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="图片 / 异常" align="center" width="110">
        <template #default="scope">
          <span>{{ scope.row.imageCount || 0 }} / {{ scope.row.anomalyCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'COMPLETED' ? 'success' : 'warning'">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="openDetail(scope.row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 巡检详情 -->
    <el-drawer v-model="detailVisible" :title="`巡检记录 #${selectedRecord?.id || ''}`" size="680px" destroy-on-close>
      <div v-if="selectedRecord" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="巡检设备">{{ selectedRecord.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="巡检区域">{{ areaName(selectedRecord.areaId) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ parseTime(selectedRecord.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ parseTime(selectedRecord.endTime) || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-title"><span>采集图片</span></div>
        <div v-if="images.length" class="image-grid">
          <article v-for="image in images" :key="image.id" class="image-card" :class="{ selected: selectedImage?.id === image.id }" @click="selectImage(image)">
            <el-image :src="resolveAssetUrl(image.filePath)" fit="cover">
              <template #error>
                <div class="image-fallback"><span>图片不可访问</span></div>
              </template>
            </el-image>
            <div>图片 #{{ image.id }}</div>
            <small>{{ parseTime(image.capturedAt) }}</small>
          </article>
        </div>
        <el-empty v-else description="该记录暂无巡检图片" :image-size="80" />
        <div class="section-title"><span>识别结果{{ selectedImage ? `（图片 #${selectedImage.id}）` : '（点击图片查看）' }}</span></div>
        <el-table v-loading="detectionsLoading" :data="detections" size="small">
          <el-table-column prop="category" label="识别类别" />
          <el-table-column label="置信度" width="100" align="center">
            <template #default="scope">
              <span>{{ percent(scope.row.confidence) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="处理状态" width="110" align="center">
            <template #default="scope">
              <el-tag size="small" :type="detectionTagType(scope.row.status)">{{ detectionLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="Inspection">
import { listInspection, getInspection, listImageDetections } from "@/api/ppdv/inspection"
import { listArea } from "@/api/ppdv/area"

const { proxy } = getCurrentInstance()

const inspectionList = ref([])
const areaList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedRecord = ref(null)
const images = ref([])
const selectedImage = ref(null)
const detections = ref([])
const detectionsLoading = ref(false)

const statusOptions = [
  { value: "COMPLETED", label: "已完成" },
  { value: "IN_PROGRESS", label: "进行中" }
]

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    keyword: undefined,
    status: undefined
  }
})
const { queryParams } = toRefs(data)

function statusLabel(status) {
  return status === "COMPLETED" ? "已完成" : "进行中"
}
function detectionLabel(status) {
  return { PENDING: "待确认", CONFIRMED: "已确认", IGNORED: "已忽略" }[status] || status || "待确认"
}
function detectionTagType(status) {
  return { PENDING: "warning", CONFIRMED: "success", IGNORED: "info" }[status] || "info"
}
function percent(value) {
  return value == null ? "-" : `${Math.round(value * 100)}%`
}
function areaLabel(area) {
  return `${area.areaCode || ""} ${area.areaName || "未命名区域"}`.trim()
}
function areaName(id) {
  const area = areaList.value.find(item => item.id === id)
  return area ? areaLabel(area) : (id ? `区域 #${id}` : "未指定")
}
function resolveAssetUrl(filePath) {
  if (!filePath) return ""
  return /^https?:\/\//.test(filePath) ? filePath : import.meta.env.VITE_APP_BASE_API + filePath
}

function getList() {
  loading.value = true
  listInspection(queryParams.value).then(response => {
    inspectionList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function loadAreas() {
  listArea().then(response => {
    areaList.value = response.data || []
  }).catch(() => {})
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function openDetail(row) {
  selectedRecord.value = row
  images.value = []
  selectedImage.value = null
  detections.value = []
  detailVisible.value = true
  detailLoading.value = true
  getInspection(row.id).then(response => {
    selectedRecord.value = response.record || row
    images.value = response.images || []
  }).finally(() => {
    detailLoading.value = false
  })
}

function selectImage(image) {
  selectedImage.value = image
  detections.value = []
  detectionsLoading.value = true
  listImageDetections(image.id).then(response => {
    detections.value = response.data || []
  }).finally(() => {
    detectionsLoading.value = false
  })
}

getList()
loadAreas()
</script>

<style scoped>
.section-title { display: flex; align-items: center; justify-content: space-between; margin: 26px 0 12px; font-size: 16px; font-weight: 650; }
.image-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.image-card { overflow: hidden; padding: 6px; border: 1px solid var(--ag-border); border-radius: 4px; cursor: pointer; }
.image-card.selected { border-color: var(--el-color-primary); box-shadow: 0 0 0 1px var(--el-color-primary); }
.image-card :deep(.el-image) { display: block; width: 100%; height: 112px; margin-bottom: 6px; background: #f4f5f2; }
.image-card div:not(.image-fallback) { font-size: 12px; }
.image-card small { color: var(--ag-muted); font-size: 10px; }
.image-fallback { height: 100%; display: flex; align-items: center; justify-content: center; color: var(--ag-muted); font-size: 11px; }
</style>
