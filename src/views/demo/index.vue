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
  <el-table ref="tableRef" :data="pagedData" style="width: 100%" @selection-change="handleSelectionChange">
    <el-table-column type="selection" width="55" />
    <el-table-column prop="date" label="日期" width="120" />
    <el-table-column prop="name" label="姓名" width="120" />
    <el-table-column prop="address" label="地址" show-overflow-tooltip />
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
      <el-form-item label="日期" prop="date">
        <el-date-picker
          v-model="formData.date"
          type="date"
          placeholder="请选择日期"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          :shortcuts="dateShortcuts"
        />
      </el-form-item>
      <el-form-item label="姓名" prop="name">
        <el-input v-model="formData.name" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="formData.address" placeholder="请输入地址" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, reactive, computed } from 'vue'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// ---------- 搜索 ----------
const searchKeyword = ref('')
const allData: User[] = []

const tableData = ref<User[]>(allData)

function handleSearch() {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) {
    tableData.value = [...allData]
    total.value = allData.length
  } else {
    const filtered = allData.filter(
      u => u.name.toLowerCase().includes(kw) || u.address.toLowerCase().includes(kw)
    )
    tableData.value = filtered
    total.value = filtered.length
  }
  currentPage.value = 1
}

function handleReset() {
  searchKeyword.value = ''
  tableData.value = [...allData]
  total.value = allData.length
  currentPage.value = 1
}

// ---------- 表格数据 & 分页 ----------
interface User {
  id: number
  date: string
  name: string
  address: string
}

let nextId = 0

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(allData.length)

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
    const selectedIds = new Set(selectedRows.value.map(r => r.id))
    for (let i = allData.length - 1; i >= 0; i--) {
      if (selectedIds.has(allData[i].id)) {
        allData.splice(i, 1)
      }
    }
    tableData.value = [...allData]
    total.value = allData.length
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

// ---------- 新增弹窗 ----------
const dialogVisible = ref(false)

const formData = reactive({ date: '', name: '', address: '' })
const formRules = {
  date:    [{ required: true, message: '请输入日期', trigger: 'blur' }],
  name:    [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  address: [{ required: true, message: '请输入地址', trigger: 'blur' }],
}
const formRef = ref()

const dateShortcuts = [
  {
    text: '今天',
    value: () => {
      const d = new Date()
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },
  },
]

function handleOpenDialog() {
  formData.date = ''
  formData.name = ''
  formData.address = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    allData.unshift({ id: ++nextId, date: formData.date, name: formData.name, address: formData.address })
    tableData.value = [...allData]
    total.value = allData.length
    dialogVisible.value = false
    ElMessage.success('新增成功')
  } catch {
    // 校验不通过，不做任何操作
  }
}
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
