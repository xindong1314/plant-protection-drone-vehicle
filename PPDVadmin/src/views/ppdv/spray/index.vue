<template>
  <div class="app-container">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">PRECISION SPRAY</div><h1>点喷复查</h1><p>创建、分配和跟踪点喷任务，确认喷洒效果并同步区域风险状态</p></div>
      <span class="admin-live">任务服务已连接</span>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 点喷任务 -->
      <el-tab-pane label="点喷任务" name="spray">
        <el-form :model="taskQuery" ref="taskQueryRef" :inline="true" v-show="showSearch">
          <el-form-item label="任务状态" prop="status">
            <el-select v-model="taskQuery.status" placeholder="全部状态" clearable style="width: 140px">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字" prop="keyword">
            <el-input v-model="taskQuery.keyword" placeholder="设备编号或区域名称" clearable style="width: 200px" @keyup.enter="handleTaskQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleTaskQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetTaskQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8 action-bar">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" v-hasPermi="['ppdv:spray:add']" @click="openCreate">创建点喷任务</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getTasks"></right-toolbar>
        </el-row>

        <el-table v-loading="taskLoading" :data="taskList">
          <el-table-column label="任务号" align="center" prop="id" width="90" />
          <el-table-column label="目标区域" align="center" min-width="150">
            <template #default="scope">
              <span>{{ areaName(scope.row.areaId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="执行设备" align="center" min-width="130">
            <template #default="scope">
              <span>{{ scope.row.deviceId || '待分配' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="任务状态" align="center" width="110">
            <template #default="scope">
              <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" align="center" prop="createTime" width="170">
            <template #default="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="完成时间" align="center" prop="completedAt" width="170">
            <template #default="scope">
              <span>{{ parseTime(scope.row.completedAt) || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="270" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button v-if="scope.row.status === 'PENDING'" link type="primary" icon="Position" v-hasPermi="['ppdv:spray:add']" @click="openAssign(scope.row)">分配设备</el-button>
              <el-button v-if="scope.row.status === 'ASSIGNED'" link type="primary" icon="Promotion" v-hasPermi="['ppdv:spray:add']" @click="openExecute(scope.row)">执行点喷</el-button>
              <el-button v-if="scope.row.status === 'IN_PROGRESS'" link type="success" v-hasPermi="['ppdv:spray:add']" @click="setStatus(scope.row, 'COMPLETED')">标记完成</el-button>
              <el-button v-if="['PENDING', 'ASSIGNED', 'IN_PROGRESS'].includes(scope.row.status)" link type="danger" v-hasPermi="['ppdv:spray:add']" @click="setStatus(scope.row, 'FAILED')">标记失败</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="taskTotal > 0" :total="taskTotal" v-model:page="taskQuery.pageNum" v-model:limit="taskQuery.pageSize" @pagination="getTasks" />
      </el-tab-pane>

      <!-- 喷后复查 -->
      <el-tab-pane label="复查记录" name="review">
        <el-form :model="reviewQuery" ref="reviewQueryRef" :inline="true" v-show="showSearch">
          <el-form-item label="复查结论" prop="result">
            <el-select v-model="reviewQuery.result" placeholder="全部结论" clearable style="width: 160px">
              <el-option v-for="item in resultOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字" prop="keyword">
            <el-input v-model="reviewQuery.keyword" placeholder="任务号或区域名称" clearable style="width: 200px" @keyup.enter="handleReviewQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleReviewQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetReviewQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8 action-bar">
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getReviews"></right-toolbar>
        </el-row>

        <el-table v-loading="reviewLoading" :data="reviewList">
          <el-table-column label="点喷任务" align="center" width="100">
            <template #default="scope">
              <span>#{{ scope.row.sprayTaskId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标区域" align="center" min-width="150">
            <template #default="scope">
              <span>{{ areaName(scope.row.areaId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="点喷完成时间" align="center" prop="completedAt" width="170">
            <template #default="scope">
              <span>{{ parseTime(scope.row.completedAt) || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="复查时间" align="center" prop="reviewTime" width="170">
            <template #default="scope">
              <span>{{ parseTime(scope.row.reviewTime) || '待复查' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="复查结论" align="center" width="140">
            <template #default="scope">
              <el-tag :type="resultTagType(scope.row.result)">{{ resultLabel(scope.row.result) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="关联图片" align="center" width="160">
            <template #default="scope">
              <span v-if="scope.row.beforeImageId || scope.row.afterImageId">喷前 #{{ scope.row.beforeImageId || '-' }} / 喷后 #{{ scope.row.afterImageId || '-' }}</span>
              <span v-else class="muted">未关联</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-button link type="primary" :disabled="scope.row.areaId == null" v-hasPermi="['ppdv:review:add']" @click="openReview(scope.row)">录入结论</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="reviewTotal > 0" :total="reviewTotal" v-model:page="reviewQuery.pageNum" v-model:limit="reviewQuery.pageSize" @pagination="getReviews" />
      </el-tab-pane>
    </el-tabs>

    <!-- 创建点喷任务 -->
    <el-dialog title="创建点喷任务" v-model="createOpen" width="460px" append-to-body destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="目标区域" required>
          <el-select v-model="createAreaId" filterable placeholder="选择高风险区域" style="width: 100%">
            <el-option v-for="area in eligibleAreas" :key="area.id" :label="areaLabel(area)" :value="area.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="creating" @click="submitCreate">创 建</el-button>
          <el-button @click="createOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 分配执行设备 -->
    <el-dialog title="分配执行设备" v-model="assignOpen" width="460px" append-to-body destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="任务">
          <span>#{{ activeTask?.id }} · {{ areaName(activeTask?.areaId) }}</span>
        </el-form-item>
        <el-form-item label="执行设备" required>
          <el-select v-model="assignDeviceId" filterable placeholder="选择在线设备" style="width: 100%">
            <el-option v-for="device in onlineDevices" :key="device.deviceId" :label="`${device.name || device.deviceId}（${device.deviceId}）`" :value="device.deviceId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="assigning" @click="submitAssign">确认分配</el-button>
          <el-button @click="assignOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 下发点喷指令 -->
    <el-dialog title="下发点喷指令" v-model="executeOpen" width="480px" append-to-body destroy-on-close>
      <el-alert title="确认后将通过 MQTT 向已分配设备下发点喷启动指令，任务状态会更新为“执行中”。" type="warning" :closable="false" show-icon style="margin-bottom: 16px" />
      <el-form label-width="100px">
        <el-form-item label="任务">
          <span>#{{ activeTask?.id }} · {{ activeTask?.deviceId }}</span>
        </el-form-item>
        <el-form-item label="喷洒时长">
          <el-input-number v-model="executeDuration" :min="1" :max="3600" />
          <span class="unit">秒</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="executing" @click="submitExecute">确认下发</el-button>
          <el-button @click="executeOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 录入复查结论 -->
    <el-dialog title="录入喷后复查结论" v-model="reviewOpen" width="480px" append-to-body destroy-on-close>
      <el-alert title="保存后会更新该区域风险状态：通过→正常；失败→高风险；需要重喷→已喷洒待复查。" type="info" :closable="false" show-icon style="margin-bottom: 16px" />
      <el-form label-width="100px">
        <el-form-item label="点喷任务">
          <span>#{{ activeReview?.sprayTaskId }} · {{ areaName(activeReview?.areaId) }}</span>
        </el-form-item>
        <el-form-item label="复查结论" required>
          <el-radio-group v-model="reviewForm.result">
            <el-radio label="PASSED">通过</el-radio>
            <el-radio label="FAILED">失败</el-radio>
            <el-radio label="NEED_RETRY">需要重喷</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="喷前图片 ID">
          <el-input-number v-model="reviewForm.beforeImageId" :min="1" :precision="0" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="喷后图片 ID">
          <el-input-number v-model="reviewForm.afterImageId" :min="1" :precision="0" :step="1" controls-position="right" />
        </el-form-item>
        <div class="image-help">图片 ID 可选；填写时必须是该任务目标区域的巡检图片，且喷前、喷后不能使用同一张图片，服务端会再次校验。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="savingReview" :disabled="!reviewForm.result" @click="submitReview">保存结论</el-button>
          <el-button @click="reviewOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Spray">
import { listSprayTasks, createSprayTask, assignSprayTask, executeSprayTask, updateSprayTaskStatus, listReviewPage, createReview } from "@/api/ppdv/spray"
import { listArea } from "@/api/ppdv/area"
import { listDevice } from "@/api/ppdv/device"

const { proxy } = getCurrentInstance()

const activeTab = ref("spray")
const showSearch = ref(true)

const taskList = ref([])
const taskLoading = ref(true)
const taskTotal = ref(0)
const reviewList = ref([])
const reviewLoading = ref(false)
const reviewTotal = ref(0)
const areaList = ref([])
const deviceList = ref([])

const createOpen = ref(false)
const creating = ref(false)
const createAreaId = ref(null)
const assignOpen = ref(false)
const assigning = ref(false)
const assignDeviceId = ref("")
const executeOpen = ref(false)
const executing = ref(false)
const executeDuration = ref(30)
const activeTask = ref(null)
const reviewOpen = ref(false)
const savingReview = ref(false)
const activeReview = ref(null)
const reviewForm = ref({ result: "", beforeImageId: null, afterImageId: null })

const statusOptions = [
  { value: "PENDING", label: "待处理" },
  { value: "ASSIGNED", label: "已分配" },
  { value: "IN_PROGRESS", label: "执行中" },
  { value: "COMPLETED", label: "已完成" },
  { value: "FAILED", label: "失败" }
]
const resultOptions = [
  { value: "NEED_RETRY", label: "待复查 / 需重喷" },
  { value: "PASSED", label: "通过" },
  { value: "FAILED", label: "失败" }
]

const data = reactive({
  taskQuery: {
    pageNum: 1,
    pageSize: 10,
    status: undefined,
    keyword: undefined
  },
  reviewQuery: {
    pageNum: 1,
    pageSize: 10,
    result: undefined,
    keyword: undefined
  }
})
const { taskQuery, reviewQuery } = toRefs(data)

const eligibleAreas = computed(() => areaList.value.filter(area => area.riskStatus === "HIGH_RISK"))
const onlineDevices = computed(() => deviceList.value.filter(device => device.status === "ONLINE"))

function statusLabel(status) {
  return statusOptions.find(item => item.value === status)?.label || status
}
function statusTagType(status) {
  return { PENDING: "info", ASSIGNED: "warning", IN_PROGRESS: "primary", COMPLETED: "success", FAILED: "danger" }[status] || "info"
}
function resultLabel(result) {
  return { PASSED: "通过", FAILED: "失败", NEED_RETRY: "待复查 / 需重喷" }[result] || result || "待复查"
}
function resultTagType(result) {
  return { PASSED: "success", FAILED: "danger", NEED_RETRY: "warning" }[result] || "info"
}
function areaLabel(area) {
  return `${area.areaCode || ""} ${area.areaName || "未命名区域"}`.trim()
}
function areaName(id) {
  const area = areaList.value.find(item => item.id === id)
  return area ? areaLabel(area) : (id ? `区域 #${id}` : "未指定")
}

function getTasks() {
  taskLoading.value = true
  listSprayTasks(taskQuery.value).then(response => {
    taskList.value = response.rows || []
    taskTotal.value = response.total || 0
  }).finally(() => {
    taskLoading.value = false
  })
}

function getReviews() {
  reviewLoading.value = true
  listReviewPage(reviewQuery.value).then(response => {
    reviewList.value = response.rows || []
    reviewTotal.value = response.total || 0
  }).finally(() => {
    reviewLoading.value = false
  })
}

function loadOptions() {
  listArea().then(response => {
    areaList.value = response.data || []
  }).catch(() => {})
  listDevice({ pageNum: 1, pageSize: 200, orderByColumn: "deviceId", isAsc: "asc" }).then(response => {
    deviceList.value = response.rows || []
  }).catch(() => {})
}

function handleTaskQuery() {
  taskQuery.value.pageNum = 1
  getTasks()
}

function resetTaskQuery() {
  proxy.resetForm("taskQueryRef")
  getTasks()
}

function handleReviewQuery() {
  reviewQuery.value.pageNum = 1
  getReviews()
}

function resetReviewQuery() {
  proxy.resetForm("reviewQueryRef")
  getReviews()
}

function openCreate() {
  createAreaId.value = eligibleAreas.value[0]?.id || null
  createOpen.value = true
}

function submitCreate() {
  if (!createAreaId.value || !eligibleAreas.value.some(area => area.id === createAreaId.value)) {
    proxy.$modal.msgWarning("请选择高风险区域")
    return
  }
  creating.value = true
  createSprayTask({ areaId: createAreaId.value }).then(() => {
    proxy.$modal.msgSuccess("点喷任务已创建")
    createOpen.value = false
    taskQuery.value.pageNum = 1
    getTasks()
  }).finally(() => {
    creating.value = false
  })
}

function openAssign(task) {
  activeTask.value = task
  assignDeviceId.value = ""
  assignOpen.value = true
}

function submitAssign() {
  if (!assignDeviceId.value) {
    proxy.$modal.msgWarning("请选择执行设备")
    return
  }
  assigning.value = true
  assignSprayTask(activeTask.value.id, assignDeviceId.value).then(() => {
    proxy.$modal.msgSuccess("设备已分配")
    assignOpen.value = false
    getTasks()
  }).finally(() => {
    assigning.value = false
  })
}

function openExecute(task) {
  activeTask.value = task
  executeDuration.value = 30
  executeOpen.value = true
}

function submitExecute() {
  executing.value = true
  executeSprayTask(activeTask.value.id, { action: "start", duration: executeDuration.value }).then(() => {
    proxy.$modal.msgSuccess("点喷指令已下发")
    executeOpen.value = false
    getTasks()
  }).finally(() => {
    executing.value = false
  })
}

function setStatus(task, status) {
  const label = statusLabel(status)
  proxy.$modal.confirm(`确认将任务 #${task.id} 标记为“${label}”？`).then(() => {
    return updateSprayTaskStatus(task.id, status)
  }).then(() => {
    proxy.$modal.msgSuccess(`任务已标记为${label}`)
    getTasks()
  }).catch(() => {})
}

function openReview(row) {
  if (row.areaId == null) {
    proxy.$modal.msgWarning("该点喷任务未关联目标区域，无法录入复查结论")
    return
  }
  activeReview.value = row
  reviewForm.value = { result: "", beforeImageId: row.beforeImageId ?? null, afterImageId: row.afterImageId ?? null }
  reviewOpen.value = true
}

function submitReview() {
  if (!reviewForm.value.result) {
    proxy.$modal.msgWarning("请选择复查结论")
    return
  }
  const beforeImageId = reviewForm.value.beforeImageId
  const afterImageId = reviewForm.value.afterImageId
  if (beforeImageId != null && afterImageId != null && beforeImageId === afterImageId) {
    proxy.$modal.msgWarning("喷前和喷后图片不能是同一张图片")
    return
  }
  savingReview.value = true
  createReview(activeReview.value.sprayTaskId, {
    areaId: activeReview.value.areaId,
    result: reviewForm.value.result,
    beforeImageId: beforeImageId || null,
    afterImageId: afterImageId || null
  }).then(() => {
    proxy.$modal.msgSuccess("复查结论已保存，区域风险状态已同步更新")
    reviewOpen.value = false
    getReviews()
  }).finally(() => {
    savingReview.value = false
  })
}

getTasks()
getReviews()
loadOptions()
</script>

<style scoped>
.unit { margin-left: 8px; color: var(--ag-muted); }
.muted { color: var(--ag-muted); }
.image-help { margin: -5px 0 0 100px; color: var(--ag-muted); font-size: 12px; line-height: 1.6; }
</style>
