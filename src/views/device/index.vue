<template>
  <!-- 搜索表单 -->
  <div class="search-bar">
    <el-input v-model="searchKeyword" style="width: 240px" placeholder="请输入您要搜索的内容" />
    <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
    <el-button :icon="Refresh" @click="handleReset">重置</el-button>
  </div>

  <!-- 新增按钮 -->
  <div class="toolbar">
    <el-button type="primary" :icon="Plus" @click="handleOpenDialog">新增</el-button>
    <el-button type="danger" :icon="Delete" @click="handleDelete" :disabled="selectedRows.length === 0">删除</el-button>
  </div>

  <!-- 数据表格 -->
  <el-table v-loading="loading" :data="pagedData" style="width: 100%" @selection-change="handleSelectionChange">
    <el-table-column type="selection" width="55" />
    <el-table-column prop="deviceId" label="设备编号" width="120" />
    <el-table-column prop="name" label="名称" width="120" />
    <el-table-column prop="model" label="型号" show-overflow-tooltip />
  </el-table>

  <!-- 分页器 -->
  <div class="pagination-bar">
    <span class="page-info">{{ pagedData.length }}/10</span>
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next, jumper"
      background
    />
  </div>

  <!-- 新增弹窗 -->
  <el-dialog v-model="dialogVisible" title="新增记录" width="500px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="formData.deviceId" placeholder="请输入设备编号" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入名称" />
      </el-form-item>
      <el-form-item label="型号" prop="model">
        <el-input v-model="formData.model" placeholder="请输入型号" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDevice, addDevice, delDevice } from '@/api/ppdv/device'

// ---------- 接口数据 ----------
interface User {
  id: number
  deviceId: string
  name: string
  model: string
}

const loading = ref(false)
const searchKeyword = ref('')
const allData = ref<User[]>([])
const tableData = ref<User[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res: any = await listDevice()
    allData.value = (res.rows || []) as User[]
    tableData.value = [...allData.value]
    total.value = res.total
    currentPage.value = 1
  } catch {
    // request.js 拦截器已弹错误提示，此处仅防止控制台出现 Uncaught 报错
  } finally {
    loading.value = false
  }
}

// ---------- 搜索 ----------
function handleSearch() {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) {
    tableData.value = [...allData.value]
    total.value = allData.value.length
  } else {
    const filtered = allData.value.filter(
      u => u.deviceId.toLowerCase().includes(kw) || u.name.toLowerCase().includes(kw) || u.model.toLowerCase().includes(kw)
    )
    tableData.value = filtered
    total.value = filtered.length
  }
  currentPage.value = 1
}

function handleReset() {
  searchKeyword.value = ''
  tableData.value = [...allData.value]
  total.value = allData.value.length
  currentPage.value = 1
}

// ---------- 分页 ----------
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return tableData.value.slice(start, start + pageSize.value)
})

// ---------- 删除 ----------
const selectedRows = ref<User[]>([])

function handleSelectionChange(rows: User[]) {
  selectedRows.value = rows
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定要删除选中的记录吗？', '提示', { type: 'warning' })
    const ids = selectedRows.value.map(r => r.id).join(',')
    await delDevice(ids)
    ElMessage.success('删除成功')
    await fetchList()
  } catch {
    // 用户取消 或 接口失败（request.js 拦截器已弹错误提示）
  }
}

// ---------- 新增弹窗 ----------
const dialogVisible = ref(false)
const submitLoading = ref(false)

const formData = reactive({ deviceId: '', name: '', model: '' })
const formRules = {
  deviceId: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name:     [{ required: true, message: '请输入名称', trigger: 'blur' }],
  model:    [{ required: true, message: '请输入型号', trigger: 'blur' }],
}
const formRef = ref()

function handleOpenDialog() {
  formData.deviceId = ''
  formData.name = ''
  formData.model = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    await addDevice({ ...formData })
    dialogVisible.value = false
    ElMessage.success('新增成功')
    await fetchList()
  } catch {
    // 校验不通过 或 接口失败，不做额外处理（request.js 拦截器已弹错误提示）
  } finally {
    submitLoading.value = false
  }
}

// ---------- 初始加载 ----------
onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar {
  margin: 16px 0;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.page-info {
  font-size: 14px;
  color: #606266;
}
</style>
