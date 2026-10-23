<template>
  <div class="app-container">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">AREA HISTORY</div><h1>区域档案</h1><p>汇总每个区域的风险状态、巡检、异常和点喷履历</p></div>
      <span class="admin-live">只读数据</span>
    </div>

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="区域编号、名称或大棚" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="当前风险" prop="riskStatus">
        <el-select v-model="queryParams.riskStatus" placeholder="全部风险" clearable style="width: 140px">
          <el-option v-for="item in riskOptions" :key="item.value" :label="item.label" :value="item.value" />
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

    <el-table v-loading="loading" :data="archiveList">
      <el-table-column label="区域编号" align="center" prop="areaCode" width="110" />
      <el-table-column label="区域名称" align="center" prop="areaName" min-width="140" />
      <el-table-column label="所属大棚" align="center" prop="greenhouseName" min-width="140" />
      <el-table-column label="当前风险" align="center" width="120">
        <template #default="scope">
          <el-tag :type="riskTagType(scope.row.riskStatus)">{{ riskLabel(scope.row.riskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="档案状态" align="center" width="100">
        <template #default="scope">
          <el-tag v-if="hasArchive(scope.row)" type="success">已建档</el-tag>
          <el-tag v-else type="info">未建档</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="巡检 / 异常 / 点喷" align="center" width="150">
        <template #default="scope">
          <span v-if="hasArchive(scope.row)">{{ scope.row.totalInspections ?? 0 }} / {{ scope.row.totalAnomalies ?? 0 }} / {{ scope.row.totalSprays ?? 0 }}</span>
          <span v-else class="muted">尚无档案</span>
        </template>
      </el-table-column>
      <el-table-column label="最近巡检" align="center" prop="lastInspectionTime" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastInspectionTime) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="openDetail(scope.row)">{{ hasArchive(scope.row) ? "查看档案" : "查看状态" }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 区域档案详情 -->
    <el-drawer v-model="detailVisible" :title="`${selectedArea?.areaCode || ''} 区域档案`" size="560px" destroy-on-close>
      <template v-if="selectedArea">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="区域名称">{{ selectedArea.areaName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属大棚">{{ selectedArea.greenhouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="ArUco 标记">{{ selectedArea.arucoId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前风险">
            <el-tag :type="riskTagType(selectedArea.riskStatus)">{{ riskLabel(selectedArea.riskStatus) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div v-loading="detailLoading" class="detail-state">
          <el-empty v-if="detailStatus === 'missing'" description="该区域尚未建立档案" :image-size="90">
            <p class="empty-note">区域基础信息已存在，但尚未产生可建档的巡检或作业记录。</p>
          </el-empty>
          <el-alert v-else-if="detailStatus === 'error'" :title="detailError || '档案详情加载失败'" type="error" :closable="false" show-icon />
          <template v-else-if="detailStatus === 'ready'">
            <h3>业务累计</h3>
            <el-row :gutter="12" class="detail-stats">
              <el-col :span="8"><div><strong>{{ archiveDetail?.totalInspections ?? 0 }}</strong><span>巡检次数</span></div></el-col>
              <el-col :span="8"><div><strong>{{ archiveDetail?.totalAnomalies ?? 0 }}</strong><span>异常次数</span></div></el-col>
              <el-col :span="8"><div><strong>{{ archiveDetail?.totalSprays ?? 0 }}</strong><span>点喷次数</span></div></el-col>
            </el-row>
            <h3>最近动态</h3>
            <el-timeline>
              <el-timeline-item v-if="archiveDetail?.lastInspectionTime" timestamp="最近巡检" type="primary">{{ parseTime(archiveDetail.lastInspectionTime) }}</el-timeline-item>
              <el-timeline-item v-if="archiveDetail?.lastAnomalyTime" timestamp="最近异常" type="warning">{{ parseTime(archiveDetail.lastAnomalyTime) }}</el-timeline-item>
              <el-timeline-item v-if="archiveDetail?.lastSprayTime" timestamp="最近点喷" type="success">{{ parseTime(archiveDetail.lastSprayTime) }}</el-timeline-item>
              <el-timeline-item v-if="!hasActivity" timestamp="暂无业务动态" type="info">该区域尚未产生巡检、异常或点喷记录</el-timeline-item>
            </el-timeline>
          </template>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="Archive">
import { listAreaArchives, getAreaArchive } from "@/api/ppdv/archive"

const { proxy } = getCurrentInstance()

const archiveList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailStatus = ref("idle")
const detailError = ref("")
const selectedArea = ref(null)
const archiveDetail = ref(null)

const riskOptions = [
  { value: "NORMAL", label: "正常" },
  { value: "SUSPICIOUS", label: "疑似异常" },
  { value: "HIGH_RISK", label: "高风险" },
  { value: "SPRAYED_PENDING_REVIEW", label: "待复查" }
]

const data = reactive({
  queryParams: {
    keyword: undefined,
    riskStatus: undefined
  }
})
const { queryParams } = toRefs(data)

const hasActivity = computed(() => archiveDetail.value && [archiveDetail.value.lastInspectionTime, archiveDetail.value.lastAnomalyTime, archiveDetail.value.lastSprayTime].some(Boolean))

function hasArchive(row) {
  return !!row && ["totalInspections", "totalAnomalies", "totalSprays", "lastRiskStatus"].some(field => Object.prototype.hasOwnProperty.call(row, field))
}
function riskLabel(status) {
  return { NORMAL: "正常", SUSPICIOUS: "疑似异常", HIGH_RISK: "高风险", SPRAYED_PENDING_REVIEW: "待复查" }[status] || status || "正常"
}
function riskTagType(status) {
  return { NORMAL: "success", SUSPICIOUS: "warning", HIGH_RISK: "danger", SPRAYED_PENDING_REVIEW: "info" }[status] || "info"
}

function getList() {
  loading.value = true
  listAreaArchives(queryParams.value).then(response => {
    archiveList.value = response.data || []
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function openDetail(row) {
  selectedArea.value = row
  archiveDetail.value = null
  detailError.value = ""
  detailVisible.value = true
  if (!hasArchive(row)) {
    detailStatus.value = "missing"
    detailLoading.value = false
    return
  }
  detailStatus.value = "loading"
  detailLoading.value = true
  getAreaArchive(row.id).then(response => {
    archiveDetail.value = response.data
    detailStatus.value = "ready"
  }).catch(error => {
    if (String(error?.message || "").includes("区域档案不存在")) {
      detailStatus.value = "missing"
    } else {
      detailStatus.value = "error"
      detailError.value = error?.message || "档案详情加载失败"
    }
  }).finally(() => {
    detailLoading.value = false
  })
}

getList()
</script>

<style scoped>
.muted { color: var(--ag-muted); }
.detail-state { min-height: 180px; margin-top: 20px; }
.empty-note { margin: 0; color: var(--ag-muted); font-size: 12px; }
.detail-stats { margin: 0 0 24px; }
.detail-stats > div > div { display: flex; flex-direction: column; padding: 16px; background: #f5f7f2; }
.detail-stats strong { font-size: 26px; }
.detail-stats span { margin-top: 5px; color: var(--ag-muted); font-size: 12px; }
.el-drawer h3 { margin: 26px 0 12px; font-size: 16px; }
</style>
