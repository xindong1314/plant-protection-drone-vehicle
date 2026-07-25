<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="设备编号" prop="deviceId">
        <el-input v-model="queryParams.deviceId" placeholder="请输入设备编号" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ppdv:device:add']">登记设备</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['ppdv:device:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="deviceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="设备编号" align="center" prop="deviceId" />
      <el-table-column label="名称" align="center" prop="name" />
      <el-table-column label="型号" align="center" prop="model" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="ppdv_device_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="电量%" align="center" prop="battery" />
      <el-table-column label="作业模式" align="center" prop="mode">
        <template #default="scope">
          <dict-tag :options="ppdv_work_mode" :value="scope.row.mode" />
        </template>
      </el-table-column>
      <el-table-column label="固件" align="center" prop="firmware" />
      <el-table-column label="密钥(一机一密)" align="center" prop="secret" width="140" show-overflow-tooltip />
      <el-table-column label="最近在线" align="center" prop="lastSeenAt" width="170">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastSeenAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Position" @click="handleCommand(scope.row)" v-hasPermi="['ppdv:device:command']">下发指令</el-button>
          <el-button link type="warning" icon="Key" @click="handleResetSecret(scope.row)" v-hasPermi="['ppdv:device:edit']">重置密钥</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ppdv:device:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 登记设备 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="deviceRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="设备编号" prop="deviceId">
          <el-input v-model="form.deviceId" placeholder="如 PPDV-0002" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入设备名称" />
        </el-form-item>
        <el-form-item label="型号" prop="model">
          <el-input v-model="form.model" placeholder="请输入型号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 下发指令 -->
    <el-dialog title="下发控制指令" v-model="cmdOpen" width="480px" append-to-body>
      <el-form ref="cmdRef" :model="cmdForm" label-width="90px">
        <el-form-item label="目标设备">
          <span>{{ cmdForm.deviceId }}</span>
        </el-form-item>
        <el-form-item label="指令" prop="cmd">
          <el-select v-model="cmdForm.cmd" placeholder="请选择指令" style="width: 100%">
            <el-option v-for="c in cmdOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="模式(可选)" v-if="cmdForm.cmd === 'SET_MODE' || cmdForm.cmd === 'START'">
          <el-select v-model="cmdForm.mode" placeholder="可选模式" clearable style="width: 100%">
            <el-option v-for="dict in ppdv_work_mode" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitCommand">下 发</el-button>
          <el-button @click="cmdOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Device">
import { listDevice, addDevice, delDevice, sendCommand, resetSecret } from "@/api/ppdv/device"

const { proxy } = getCurrentInstance()
const { ppdv_device_status, ppdv_work_mode } = useDict("ppdv_device_status", "ppdv_work_mode")

const deviceList = ref([])
const open = ref(false)
const cmdOpen = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const cmdOptions = ["START", "PAUSE", "RESUME", "STOP", "ESTOP", "RTL", "SET_MODE"]

const data = reactive({
  form: {},
  cmdForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    deviceId: undefined
  },
  rules: {
    deviceId: [{ required: true, message: "设备编号不能为空", trigger: "blur" }]
  }
})
const { queryParams, form, cmdForm, rules } = toRefs(data)

function getList() {
  loading.value = true
  listDevice(queryParams.value).then(response => {
    let rows = response.rows
    if (queryParams.value.deviceId) {
      rows = rows.filter(r => r.deviceId.includes(queryParams.value.deviceId))
    }
    deviceList.value = rows
    total.value = response.total
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = { deviceId: undefined, name: undefined, model: undefined }
  proxy.resetForm("deviceRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "登记设备"
}

function submitForm() {
  proxy.$refs["deviceRef"].validate(valid => {
    if (valid) {
      addDevice(form.value).then(() => {
        proxy.$modal.msgSuccess("登记成功，已生成一机一密密钥")
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const delIds = row.id || ids.value
  proxy.$modal.confirm('是否确认删除所选设备？').then(function () {
    return delDevice(delIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

function handleCommand(row) {
  cmdForm.value = { deviceId: row.deviceId, cmd: "START", mode: undefined }
  cmdOpen.value = true
}

function submitCommand() {
  const payload = { cmd: cmdForm.value.cmd }
  if (cmdForm.value.mode) {
    payload.args = { mode: cmdForm.value.mode }
  }
  sendCommand(cmdForm.value.deviceId, payload).then(res => {
    proxy.$modal.msgSuccess("指令已下发, msgId=" + res.data)
    cmdOpen.value = false
  })
}

function handleResetSecret(row) {
  proxy.$modal.confirm('重置后旧密钥立即失效，需重新配置车载端，确认重置 ' + row.deviceId + ' 的密钥？').then(function () {
    return resetSecret(row.deviceId)
  }).then(res => {
    proxy.$modal.alert("新密钥：" + res.data)
    getList()
  }).catch(() => {})
}

getList()
</script>
