<template>
  <div class="archive-page">
    <div class="page-heading">
      <div><div class="eyebrow">AREA HISTORY</div><h1>区域档案</h1><p>汇总每个区域的风险状态、巡检、异常和点喷履历</p></div>
      <el-button :icon="Refresh" @click="loadArchives">刷新数据</el-button>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :span="6"><el-card><div class="summary-label">建档区域</div><div class="summary-value">{{ archives.length }}</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="summary-label">高风险区域</div><div class="summary-value high">{{ riskCount('HIGH_RISK') }}</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="summary-label">累计巡检</div><div class="summary-value">{{ total('totalInspections') }}</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="summary-label">累计点喷</div><div class="summary-value">{{ total('totalSprays') }}</div></el-card></el-col>
    </el-row>

    <section class="surface-card archive-card">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="搜索区域编号、名称或大棚" :prefix-icon="Search" /><el-select v-model="riskFilter" clearable placeholder="全部风险" style="width:160px"><el-option v-for="item in riskOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-button :icon="Refresh" @click="loadArchives">刷新</el-button></div>
      <el-table v-loading="loading" :data="filteredArchives" @row-click="openDetail">
        <el-table-column prop="areaCode" label="区域编号" width="125" />
        <el-table-column prop="areaName" label="区域名称" min-width="150" />
        <el-table-column prop="greenhouseName" label="所属大棚" min-width="150" />
        <el-table-column label="当前风险" width="140"><template #default="{ row }"><el-tag :type="riskTagType(row.riskStatus)">{{ riskLabel(row.riskStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="巡检 / 异常 / 点喷" width="160"><template #default="{ row }">{{ row.totalInspections || 0 }} / {{ row.totalAnomalies || 0 }} / {{ row.totalSprays || 0 }}</template></el-table-column>
        <el-table-column label="最近巡检" min-width="175"><template #default="{ row }">{{ formatTime(row.lastInspectionTime) }}</template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button type="primary" link @click.stop="openDetail(row)">查看档案</el-button></template></el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" :title="`${selectedArea?.areaCode || ''} 区域档案`" size="560px" destroy-on-close>
      <template v-if="selectedArea">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="区域名称">{{ selectedArea.areaName || '-' }}</el-descriptions-item><el-descriptions-item label="所属大棚">{{ selectedArea.greenhouseName || '-' }}</el-descriptions-item><el-descriptions-item label="ArUco 标记">{{ selectedArea.arucoId || '-' }}</el-descriptions-item><el-descriptions-item label="当前风险"><el-tag :type="riskTagType(selectedArea.riskStatus)">{{ riskLabel(selectedArea.riskStatus) }}</el-tag></el-descriptions-item>
        </el-descriptions>
        <h3>业务累计</h3>
        <el-row :gutter="12" class="detail-stats"><el-col :span="8"><div><strong>{{ archiveDetail?.totalInspections || 0 }}</strong><span>巡检次数</span></div></el-col><el-col :span="8"><div><strong>{{ archiveDetail?.totalAnomalies || 0 }}</strong><span>异常次数</span></div></el-col><el-col :span="8"><div><strong>{{ archiveDetail?.totalSprays || 0 }}</strong><span>点喷次数</span></div></el-col></el-row>
        <h3>最近动态</h3>
        <el-timeline><el-timeline-item v-if="archiveDetail?.lastInspectionTime" timestamp="最近巡检" type="primary">{{ formatTime(archiveDetail.lastInspectionTime) }}</el-timeline-item><el-timeline-item v-if="archiveDetail?.lastAnomalyTime" timestamp="最近异常" type="warning">{{ formatTime(archiveDetail.lastAnomalyTime) }}</el-timeline-item><el-timeline-item v-if="archiveDetail?.lastSprayTime" timestamp="最近点喷" type="success">{{ formatTime(archiveDetail.lastSprayTime) }}</el-timeline-item><el-timeline-item v-if="!hasActivity" timestamp="暂无业务动态" type="info">该区域尚未产生巡检、异常或点喷记录</el-timeline-item></el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getAreaArchive, listAreaArchives } from '@/api/archive'

const riskOptions = [{ value: 'NORMAL', label: '正常' }, { value: 'SUSPICIOUS', label: '疑似异常' }, { value: 'HIGH_RISK', label: '高风险' }, { value: 'SPRAYED_PENDING_REVIEW', label: '待复查' }]
const archives = ref([]), loading = ref(false), keyword = ref(''), riskFilter = ref('')
const detailVisible = ref(false), selectedArea = ref(null), archiveDetail = ref(null)
const filteredArchives = computed(() => { const key = keyword.value.trim().toLowerCase(); return archives.value.filter(row => (!riskFilter.value || row.riskStatus === riskFilter.value) && (!key || `${row.areaCode || ''} ${row.areaName || ''} ${row.greenhouseName || ''}`.toLowerCase().includes(key))) })
const hasActivity = computed(() => archiveDetail.value && [archiveDetail.value.lastInspectionTime, archiveDetail.value.lastAnomalyTime, archiveDetail.value.lastSprayTime].some(Boolean))
function responseData(response) { return response.data ?? response }
function total(field) { return archives.value.reduce((sum, row) => sum + (Number(row[field]) || 0), 0) }
function riskCount(status) { return archives.value.filter(row => row.riskStatus === status).length }
function riskLabel(status) { return { NORMAL: '正常', SUSPICIOUS: '疑似异常', HIGH_RISK: '高风险', SPRAYED_PENDING_REVIEW: '待复查' }[status] || status || '正常' }
function riskTagType(status) { return { NORMAL: 'success', SUSPICIOUS: 'warning', HIGH_RISK: 'danger', SPRAYED_PENDING_REVIEW: 'info' }[status] || 'info' }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
async function loadArchives() { loading.value = true; try { archives.value = responseData(await listAreaArchives()) || [] } finally { loading.value = false } }
async function openDetail(row) { selectedArea.value = row; archiveDetail.value = null; detailVisible.value = true; try { archiveDetail.value = responseData(await getAreaArchive(row.id)) } catch { archiveDetail.value = row } }
loadArchives()
</script>

<style scoped>
.page-heading { display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:18px; }.eyebrow { color:var(--ag-primary); font-size:11px; font-weight:700; letter-spacing:1.5px; }.page-heading h1 { margin:5px 0 6px; font-size:28px; }.page-heading p { margin:0; color:var(--ag-muted); }.summary-row { margin-bottom:18px; }.summary-row :deep(.el-card__body) { padding:18px 22px; }.summary-label { color:var(--ag-muted); font-size:13px; }.summary-value { margin-top:7px; color:var(--ag-ink); font-size:32px; font-weight:700; }.summary-value.high { color:#d93025; }.archive-card { overflow:hidden; }.toolbar { display:flex; gap:10px; padding:14px; }.toolbar .el-input { max-width:280px; }.detail-stats { margin:0 0 24px; }.detail-stats > div > div { display:flex; flex-direction:column; padding:16px; background:#f5f7f2; }.detail-stats strong { color:var(--ag-ink); font-size:26px; }.detail-stats span { margin-top:5px; color:var(--ag-muted); font-size:12px; }.el-drawer h3 { margin:26px 0 12px; font-size:16px; }
</style>
