<template>
  <div class="app-container">
    <div class="admin-page-heading">
      <div><div class="admin-eyebrow">RISK ENGINE</div><h1>风险配置</h1><p>配置识别、环境和历史评分规则与风险阈值，修改后立即生效</p></div>
      <span class="admin-live">{{ configList.length }} 条规则</span>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeCategory">
        <el-tab-pane v-for="group in groups" :key="group.key" :label="group.label" :name="group.key">
          <el-table v-loading="loading" :data="configsByCategory(group.key)">
            <el-table-column label="规则说明" align="left" prop="description" min-width="240" />
            <el-table-column label="规则标识" align="left" prop="configKey" min-width="220" />
            <el-table-column label="当前值" align="center" width="200">
              <template #default="scope">
                <el-input-number v-model="draftValues[scope.row.id]" :precision="0" :step="1" :min="configMin(scope.row)" :max="configMax(scope.row)" :disabled="!canEdit || savingId !== null" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center" class-name="small-padding fixed-width">
              <template #default="scope">
                <el-button link type="primary" :disabled="savingId !== null || String(draftValues[scope.row.id]) === String(scope.row.configValue)" :loading="savingId === scope.row.id" v-hasPermi="['ppdv:risk:config:edit']" @click="saveConfig(scope.row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup name="RiskConfig">
import { checkPermi } from "@/utils/permission"
import { listRiskConfigs, updateRiskConfig } from "@/api/ppdv/risk"

const { proxy } = getCurrentInstance()

const configList = ref([])
const draftValues = ref({})
const loading = ref(true)
const savingId = ref(null)
const activeCategory = ref("DETECTION")
const canEdit = ref(false)

const groups = [
  { key: "DETECTION", label: "识别评分" },
  { key: "ENVIRONMENT", label: "环境评分" },
  { key: "HISTORY", label: "历史评分" },
  { key: "THRESHOLD", label: "风险阈值" }
]

function configsByCategory(category) {
  return configList.value.filter(config => config.category === category)
}
function configValue(key, fallback) {
  const item = configList.value.find(config => config.configKey === key)
  const value = Number(item?.configValue)
  return Number.isFinite(value) ? value : fallback
}
function configMin(config) {
  if (config.configKey === "risk.history.spray_ok") return -100
  if (config.configKey === "risk.threshold.high") return 1
  return 0
}
function configMax(config) {
  if (config.configKey === "risk.history.spray_ok") return 0
  if (config.configKey === "risk.threshold.suspected") return 99
  return 100
}

function getList() {
  loading.value = true
  listRiskConfigs().then(response => {
    const grouped = response.data || {}
    configList.value = Object.values(grouped).flat()
    draftValues.value = Object.fromEntries(configList.value.map(item => [item.id, Number(item.configValue)]))
  }).finally(() => {
    loading.value = false
  })
}

function saveConfig(config) {
  const value = draftValues.value[config.id]
  if (!Number.isSafeInteger(value) || value < configMin(config) || value > configMax(config)) {
    proxy.$modal.msgWarning("请输入允许范围内的整数")
    return
  }
  const suspected = config.configKey === "risk.threshold.suspected" ? value : configValue("risk.threshold.suspected", 21)
  const high = config.configKey === "risk.threshold.high" ? value : configValue("risk.threshold.high", 51)
  if (suspected >= high) {
    proxy.$modal.msgWarning("疑似异常阈值必须小于高风险阈值")
    return
  }
  savingId.value = config.id
  updateRiskConfig(config.id, String(value)).then(response => {
    const updated = response.data || {}
    config.configValue = updated.configValue ?? String(value)
    draftValues.value[config.id] = Number(config.configValue)
    proxy.$modal.msgSuccess("规则已保存")
  }).finally(() => {
    savingId.value = null
  })
}

canEdit.value = checkPermi(["ppdv:risk:config:edit"])
getList()
</script>
