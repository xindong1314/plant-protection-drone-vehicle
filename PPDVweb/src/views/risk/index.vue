<template>
  <div class="risk-page">
    <div class="page-heading">
      <div><div class="eyebrow">RISK ENGINE</div><h1>风险规则与计算</h1><p>配置识别、环境和历史规则，并重新计算区域风险</p></div>
      <el-button :icon="Refresh" @click="loadData">刷新数据</el-button>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :span="8"><el-card><div class="summary-label">区域总数</div><div class="summary-value">{{ areas.length }}</div></el-card></el-col>
      <el-col :span="8"><el-card><div class="summary-label">疑似异常</div><div class="summary-value suspicious">{{ suspiciousCount }}</div></el-card></el-col>
      <el-col :span="8"><el-card><div class="summary-label">高风险区域</div><div class="summary-value high">{{ highRiskCount }}</div></el-card></el-col>
    </el-row>

    <section class="surface-card section-card">
      <div class="section-head"><div><h2>区域风险计算</h2><p>使用当前规则，汇总识别结果、环境数据和历史记录，更新区域风险分数。</p></div></div>
      <el-table v-loading="areaLoading" :data="areas">
        <el-table-column prop="areaCode" label="区域编号" width="120" />
        <el-table-column prop="areaName" label="区域名称" min-width="160" />
        <el-table-column label="当前风险" width="135"><template #default="{ row }"><el-tag :type="riskTagType(row.riskStatus)">{{ riskLabel(row.riskStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="风险分数" width="130"><template #default="{ row }"><el-progress :percentage="clampScore(row.riskScore)" :show-text="false" :stroke-width="10" :color="scoreColor(row.riskScore)" /><span class="score">{{ row.riskScore ?? 0 }}</span></template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button type="primary" link :loading="evaluatingId === row.id" @click="evaluateArea(row)">重新计算</el-button></template></el-table-column>
      </el-table>
    </section>

    <section class="surface-card section-card">
      <div class="section-head"><div><h2>风险规则配置</h2><p>修改数值后保存，后续的风险计算将立即采用新规则。</p></div><el-tag type="info">{{ configCount }} 条规则</el-tag></div>
      <el-tabs v-model="activeCategory">
        <el-tab-pane v-for="group in groups" :key="group.key" :label="group.label" :name="group.key">
          <el-table v-loading="configLoading" :data="configsByCategory(group.key)">
            <el-table-column prop="description" label="规则说明" min-width="260" />
            <el-table-column prop="configKey" label="规则标识" min-width="235" />
            <el-table-column label="当前值" width="180"><template #default="{ row }"><el-input-number v-model="draftValues[row.id]" :precision="0" :step="1" /></template></el-table-column>
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button type="primary" link :disabled="String(draftValues[row.id]) === String(row.configValue)" :loading="savingId === row.id" @click="saveConfig(row)">保存</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="resultVisible" title="风险计算结果" width="440px" destroy-on-close>
      <el-descriptions v-if="evaluationResult" :column="1" border>
        <el-descriptions-item label="区域">{{ evaluationResult.areaName }}</el-descriptions-item>
        <el-descriptions-item label="总分"><strong class="result-score">{{ evaluationResult.score }}</strong></el-descriptions-item>
        <el-descriptions-item label="风险等级"><el-tag :type="riskTagType(evaluationResult.status)">{{ riskLabel(evaluationResult.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="检测评分">{{ evaluationResult.detectionScore }}</el-descriptions-item>
        <el-descriptions-item label="环境评分">{{ evaluationResult.envScore }}</el-descriptions-item>
        <el-descriptions-item label="历史评分">{{ evaluationResult.historyScore }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="resultVisible = false">知道了</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { evaluateAreaRisk, listRiskAreas, listRiskConfigs, updateRiskConfig } from '@/api/risk'

const groups = [
  { key: 'DETECTION', label: '识别评分' },
  { key: 'ENVIRONMENT', label: '环境评分' },
  { key: 'HISTORY', label: '历史评分' },
  { key: 'THRESHOLD', label: '风险阈值' }
]
const areas = ref([]), configs = ref([]), draftValues = ref({})
const areaLoading = ref(false), configLoading = ref(false), evaluatingId = ref(null), savingId = ref(null)
const activeCategory = ref('DETECTION'), resultVisible = ref(false), evaluationResult = ref(null)
const configCount = computed(() => configs.value.length)
const suspiciousCount = computed(() => areas.value.filter(area => area.riskStatus === 'SUSPICIOUS').length)
const highRiskCount = computed(() => areas.value.filter(area => area.riskStatus === 'HIGH_RISK').length)

function responseData(response) { return response.data ?? response }
function configsByCategory(category) { return configs.value.filter(config => config.category === category) }
function clampScore(score) { return Math.max(0, Math.min(100, Number(score) || 0)) }
function scoreColor(score) { const value = clampScore(score); return value >= 51 ? '#d93025' : value >= 21 ? '#c77700' : '#76b900' }
function riskTagType(status) { return { NORMAL: 'success', SUSPICIOUS: 'warning', HIGH_RISK: 'danger', SPRAYED_PENDING_REVIEW: 'info' }[status] || 'info' }
function riskLabel(status) { return { NORMAL: '正常', SUSPICIOUS: '疑似异常', HIGH_RISK: '高风险', SPRAYED_PENDING_REVIEW: '待复查' }[status] || status || '正常' }

async function loadAreas() { areaLoading.value = true; try { areas.value = responseData(await listRiskAreas()) || [] } finally { areaLoading.value = false } }
async function loadConfigs() { configLoading.value = true; try { const grouped = responseData(await listRiskConfigs()) || {}; configs.value = Object.values(grouped).flat(); draftValues.value = Object.fromEntries(configs.value.map(item => [item.id, Number(item.configValue)])) } finally { configLoading.value = false } }
async function loadData() { await Promise.all([loadAreas(), loadConfigs()]) }
async function saveConfig(config) { const value = draftValues.value[config.id]; if (!Number.isFinite(value)) return ElMessage.warning('请输入有效的整数'); savingId.value = config.id; try { const updated = responseData(await updateRiskConfig(config.id, String(value))); config.configValue = updated.configValue ?? String(value); draftValues.value[config.id] = Number(config.configValue); ElMessage.success('规则已保存') } finally { savingId.value = null } }
async function evaluateArea(area) { evaluatingId.value = area.id; try { const result = responseData(await evaluateAreaRisk(area.id)); evaluationResult.value = { ...result, areaName: `${area.areaCode || ''} ${area.areaName || ''}`.trim() }; resultVisible.value = true; await loadAreas() } finally { evaluatingId.value = null } }
onMounted(loadData)
</script>

<style scoped>
.page-heading { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:18px; }.eyebrow { color:var(--ag-primary); font-size:11px; font-weight:700; letter-spacing:1.5px; }.page-heading h1 { margin:5px 0 6px; font-size:28px; }.page-heading p, .section-head p { margin:0; color:var(--ag-muted); }.summary-row { margin-bottom:18px; }.summary-row :deep(.el-card__body) { padding:18px 22px; }.summary-label { color:var(--ag-muted); font-size:13px; }.summary-value { margin-top:7px; color:var(--ag-ink); font-size:32px; font-weight:700; }.summary-value.suspicious { color:#c77700; }.summary-value.high { color:#d93025; }.section-card { margin-bottom:18px; overflow:hidden; }.section-head { display:flex; justify-content:space-between; align-items:flex-start; padding:18px 20px 14px; }.section-head h2 { margin:0 0 7px; font-size:16px; }.section-card :deep(.el-tabs__nav-wrap) { padding-left:20px; }.section-card :deep(.el-tabs__nav-wrap::after) { left:20px; }.score { display:inline-block; width:28px; margin-left:8px; text-align:right; color:var(--ag-muted); font-size:12px; }.result-score { color:var(--ag-ink); font-size:22px; }
</style>
