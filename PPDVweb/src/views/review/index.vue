<template>
  <div class="review-page">
    <div class="page-heading">
      <div><div class="eyebrow">POST-SPRAY REVIEW</div><h1>喷后复查</h1><p>确认点喷效果，并将复查结论同步至区域风险状态</p></div>
      <el-button :icon="Refresh" @click="loadData">刷新数据</el-button>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col v-for="item in summaryItems" :key="item.value" :span="8"><el-card><div class="summary-label">{{ item.label }}</div><div class="summary-value" :class="item.value.toLowerCase()">{{ resultCount(item.value) }}</div></el-card></el-col>
    </el-row>

    <section class="surface-card board-card">
      <div class="toolbar"><el-select v-model="filterResult" clearable placeholder="本页全部结论" style="width:160px"><el-option v-for="item in resultOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-input v-model="keyword" clearable placeholder="筛选当前页任务号或区域" :prefix-icon="Search" /><el-button :icon="Refresh" @click="loadData">刷新</el-button></div>
      <el-table v-loading="loading" :data="filteredRows">
        <el-table-column prop="sprayTaskId" label="点喷任务" width="110"><template #default="{ row }">#{{ row.sprayTaskId }}</template></el-table-column>
        <el-table-column label="目标区域" min-width="165"><template #default="{ row }">{{ areaName(row.areaId) }}</template></el-table-column>
        <el-table-column label="点喷完成时间" min-width="175"><template #default="{ row }">{{ formatTime(row.completedAt) }}</template></el-table-column>
        <el-table-column label="复查时间" min-width="175"><template #default="{ row }">{{ formatTime(row.reviewTime) }}</template></el-table-column>
        <el-table-column label="复查结论" width="140"><template #default="{ row }"><el-tag v-if="row.reviewLoadFailed" type="danger">加载失败</el-tag><el-tag v-else :type="resultTagType(row.result)">{{ resultLabel(row.result) }}</el-tag></template></el-table-column>
        <el-table-column label="关联图片" width="130"><template #default="{ row }"><span v-if="row.beforeImageId || row.afterImageId">喷前 #{{ row.beforeImageId || '-' }} / 喷后 #{{ row.afterImageId || '-' }}</span><span v-else class="muted">未关联</span></template></el-table-column>
        <el-table-column label="操作" width="125" fixed="right"><template #default="{ row }"><el-button v-if="canAddReview" type="primary" link :disabled="row.areaId == null" @click="openReview(row)">录入结论</el-button><span v-else class="muted">无录入权限</span></template></el-table-column>
      </el-table>
      <div class="pagination-bar">
        <span class="pagination-note">按点喷任务分页，当前页仅展示已完成任务</span>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="taskTotal"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="reviewVisible" title="录入喷后复查结论" width="480px" destroy-on-close>
      <el-alert title="保存后会更新该区域风险状态：通过→正常；失败→高风险；需要重喷→已喷洒待复查。" type="info" :closable="false" show-icon class="review-alert" />
      <el-form :model="reviewForm" label-width="100px"><el-form-item label="点喷任务"><span>#{{ activeRow?.sprayTaskId }} · {{ areaName(activeRow?.areaId) }}</span></el-form-item><el-form-item label="复查结论" required><el-radio-group v-model="reviewForm.result"><el-radio label="PASSED">通过</el-radio><el-radio label="FAILED">失败</el-radio><el-radio label="NEED_RETRY">需要重喷</el-radio></el-radio-group><div class="form-help">请根据本次复查显式选择，系统不会自动沿用或改写原结论。</div></el-form-item><el-form-item label="喷前图片 ID"><el-input-number v-model="reviewForm.beforeImageId" :min="1" :max="maxImageId" :precision="0" :step="1" controls-position="right" /></el-form-item><el-form-item label="喷后图片 ID"><el-input-number v-model="reviewForm.afterImageId" :min="1" :max="maxImageId" :precision="0" :step="1" controls-position="right" /></el-form-item><div class="image-help">图片 ID 可选；填写时必须是该任务目标区域的巡检图片，且喷前、喷后不能使用同一张图片。服务端会再次校验。</div></el-form>
      <template #footer><el-button @click="reviewVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!reviewForm.result" @click="submitReview">保存结论</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { createReview, listReviewAreas, listReviewPage } from '@/api/review'
import { useUserStore } from '@/store/user'

const resultOptions = [{ value: 'NEED_RETRY', label: '待复查 / 需重喷' }, { value: 'PASSED', label: '通过' }, { value: 'FAILED', label: '失败' }]
const summaryItems = resultOptions.map(item => ({ ...item, label: `本页${item.label}` }))
const userStore = useUserStore()
const areas = ref([]), rows = ref([]), loading = ref(false), keyword = ref(''), filterResult = ref('')
const pageNum = ref(1), pageSize = ref(20), taskTotal = ref(0)
const reviewVisible = ref(false), saving = ref(false), activeRow = ref(null), reviewForm = ref({ result: '', beforeImageId: null, afterImageId: null })
const maxImageId = Number.MAX_SAFE_INTEGER
const canAddReview = computed(() => userStore.hasPermission('ppdv:review:add'))
const canListAreas = computed(() => userStore.hasPermission('ppdv:area:list'))
const filteredRows = computed(() => { const key = keyword.value.trim().toLowerCase(); return rows.value.filter(row => (!filterResult.value || row.result === filterResult.value) && (!key || `#${row.sprayTaskId} ${areaName(row.areaId)}`.toLowerCase().includes(key))) })

let loadRequestId = 0
let areaRequestId = 0

function responseData(response) { return response.data ?? response }
function areaLabel(area) { return `${area.areaCode || ''} ${area.areaName || '未命名区域'}`.trim() }
function areaName(id) { const area = areas.value.find(item => item.id === id); return area ? areaLabel(area) : (id ? `区域 #${id}` : '未指定') }
function resultCount(result) { return rows.value.filter(row => row.result === result).length }
function resultLabel(result) { return { PASSED: '通过', FAILED: '失败', NEED_RETRY: '待复查 / 需重喷' }[result] || result || '待复查' }
function resultTagType(result) { return { PASSED: 'success', FAILED: 'danger', NEED_RETRY: 'warning' }[result] || 'info' }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }

async function loadAreas() {
  const requestId = ++areaRequestId
  if (!canListAreas.value) {
    areas.value = []
    return
  }
  try {
    const result = await listReviewAreas()
    if (requestId === areaRequestId) areas.value = responseData(result) || []
  } catch {
    if (requestId === areaRequestId) areas.value = []
  }
}

async function loadData() {
  const requestId = ++loadRequestId
  loadAreas()
  loading.value = true
  try {
    const taskResult = await listReviewPage({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (requestId !== loadRequestId) return
    taskTotal.value = Number(taskResult.total) || 0
    rows.value = (taskResult.rows || []).map(item => ({ ...item, reviewLoadFailed: false }))
  } catch {
    if (requestId === loadRequestId) {
      rows.value = []
      taskTotal.value = 0
    }
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}
function handlePageSizeChange() { pageNum.value = 1; loadData() }
function openReview(row) { if (row.areaId == null) return ElMessage.warning('该点喷任务未关联目标区域，无法录入复查结论'); activeRow.value = row; reviewForm.value = { result: '', beforeImageId: row.beforeImageId ?? null, afterImageId: row.afterImageId ?? null }; reviewVisible.value = true }
function normalizeImageId(value) {
  if (value == null || value === '') return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : NaN
}
async function submitReview() {
  if (!activeRow.value?.sprayTaskId) return ElMessage.warning('请选择有效的点喷任务')
  if (!resultOptions.some(item => item.value === reviewForm.value.result)) return ElMessage.warning('请选择复查结论')
  const beforeImageId = normalizeImageId(reviewForm.value.beforeImageId)
  const afterImageId = normalizeImageId(reviewForm.value.afterImageId)
  if (Number.isNaN(beforeImageId) || Number.isNaN(afterImageId)) return ElMessage.warning('图片 ID 必须是有效的正整数')
  if (beforeImageId != null && beforeImageId === afterImageId) return ElMessage.warning('喷前和喷后图片不能是同一张图片')
  saving.value = true
  try {
    await createReview(activeRow.value.sprayTaskId, { areaId: activeRow.value.areaId, result: reviewForm.value.result, beforeImageId, afterImageId })
    ElMessage.success('复查结论已保存，区域风险状态已同步更新')
    reviewVisible.value = false
    await loadData()
  } finally { saving.value = false }
}
onMounted(loadData)
</script>

<style scoped>
.page-heading { display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:18px; }.eyebrow { color:var(--ag-primary); font-size:11px; font-weight:700; letter-spacing:1.5px; }.page-heading h1 { margin:5px 0 6px; font-size:28px; }.page-heading p { margin:0; color:var(--ag-muted); }.summary-row { margin-bottom:18px; }.summary-row :deep(.el-card__body) { padding:18px 22px; }.summary-label { color:var(--ag-muted); font-size:13px; }.summary-value { margin-top:7px; color:var(--ag-ink); font-size:32px; font-weight:700; }.summary-value.need_retry { color:#c77700; }.summary-value.passed { color:#76b900; }.summary-value.failed { color:#d93025; }.board-card { overflow:hidden; }.toolbar { display:flex; gap:10px; padding:14px; }.toolbar .el-input { max-width:280px; }.pagination-bar { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:12px; padding:14px; }.pagination-note, .form-help, .image-help { color:var(--ag-muted); font-size:12px; }.form-help { width:100%; margin-top:6px; line-height:1.5; }.image-help { margin:-5px 0 0 100px; line-height:1.6; }.review-alert { margin-bottom:20px; }.muted { color:var(--ag-muted); }
</style>
