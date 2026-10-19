<template>
  <div class="app-container">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">RISK ENGINE</div><h1>风险评估</h1><p>按当前规则汇总识别、环境与历史记录，重新计算区域风险</p></div>
      <span class="admin-live">区域 {{ areaList.length }} 个</span>
    </div>

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="关键字" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="区域编号或名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8 action-bar">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="filteredList">
      <el-table-column label="区域编号" align="center" prop="areaCode" width="110" />
      <el-table-column label="区域名称" align="center" prop="areaName" min-width="150" />
      <el-table-column label="所属大棚" align="center" prop="greenhouseName" min-width="140" />
      <el-table-column label="当前风险" align="center" width="120">
        <template #default="scope">
          <el-tag :type="riskTagType(scope.row.riskStatus)">{{ riskLabel(scope.row.riskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风险分数" align="center" width="180">
        <template #default="scope">
          <div class="score-cell">
            <el-progress :percentage="clampScore(scope.row.riskScore)" :show-text="false" :stroke-width="10" :color="scoreColor(scope.row.riskScore)" />
            <span class="score">{{ scope.row.riskScore ?? 0 }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="最近评估" align="center" prop="lastEvalAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastEvalAt) || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Refresh" :loading="evaluatingId === scope.row.id" v-hasPermi="['ppdv:risk:evaluate']" @click="handleEvaluate(scope.row)">重新计算</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 风险计算结果 -->
    <el-dialog title="风险计算结果" v-model="resultOpen" width="440px" append-to-body destroy-on-close>
      <el-descriptions v-if="evaluationResult" :column="1" border>
        <el-descriptions-item label="区域">{{ evaluationResult.areaName }}</el-descriptions-item>
        <el-descriptions-item label="总分"><strong class="result-score">{{ evaluationResult.score }}</strong></el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskTagType(evaluationResult.status)">{{ riskLabel(evaluationResult.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检测评分">{{ evaluationResult.detectionScore }}</el-descriptions-item>
        <el-descriptions-item label="环境评分">{{ evaluationResult.envScore }}</el-descriptions-item>
        <el-descriptions-item label="历史评分">{{ evaluationResult.historyScore }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="resultOpen = false">知道了</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="RiskEval">
import { listArea } from "@/api/ppdv/area"
import { listRiskConfigs, evaluateAreaRisk } from "@/api/ppdv/risk"

const { proxy } = getCurrentInstance()

const areaList = ref([])
const configList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const evaluatingId = ref(null)
const resultOpen = ref(false)
const evaluationResult = ref(null)

const data = reactive({
  queryParams: {
    keyword: undefined
  }
})
const { queryParams } = toRefs(data)

const filteredList = computed(() => {
  const text = queryParams.value.keyword?.trim().toLowerCase()
  return text ? areaList.value.filter(area => `${area.areaCode} ${area.areaName}`.toLowerCase().includes(text)) : areaList.value
})

function riskLabel(status) {
  return { NORMAL: "正常", SUSPICIOUS: "疑似异常", HIGH_RISK: "高风险", SPRAYED_PENDING_REVIEW: "待复查" }[status] || status || "正常"
}
function riskTagType(status) {
  return { NORMAL: "success", SUSPICIOUS: "warning", HIGH_RISK: "danger", SPRAYED_PENDING_REVIEW: "info" }[status] || "info"
}
function clampScore(score) {
  return Math.max(0, Math.min(100, Number(score) || 0))
}
function configValue(key, fallback) {
  const item = configList.value.find(config => config.configKey === key)
  const value = Number(item?.configValue)
  return Number.isFinite(value) ? value : fallback
}
function scoreColor(score) {
  const value = clampScore(score)
  const suspicious = configValue("risk.threshold.suspected", 21)
  const high = configValue("risk.threshold.high", 51)
  return value >= high ? "#d93025" : value >= suspicious ? "#c77700" : "#76b900"
}

function getList() {
  loading.value = true
  listArea().then(response => {
    areaList.value = response.data || []
  }).finally(() => {
    loading.value = false
  })
}

function loadConfigs() {
  listRiskConfigs().then(response => {
    const grouped = response.data || {}
    configList.value = Object.values(grouped).flat()
  }).catch(() => {})
}

function handleQuery() {
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleEvaluate(area) {
  evaluatingId.value = area.id
  evaluateAreaRisk(area.id).then(response => {
    evaluationResult.value = { ...(response.data || {}), areaName: `${area.areaCode || ""} ${area.areaName || ""}`.trim() }
    resultOpen.value = true
    getList()
  }).finally(() => {
    evaluatingId.value = null
  })
}

getList()
loadConfigs()
</script>

<style scoped>
.score-cell { display: flex; align-items: center; }
.score-cell :deep(.el-progress) { flex: 1; }
.score { display: inline-block; width: 28px; margin-left: 8px; text-align: right; color: var(--ag-muted); font-size: 12px; }
.result-score { font-size: 22px; }
</style>
