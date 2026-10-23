import request from '@/utils/request'

// 查询点喷任务列表（分页，可按状态筛选）
export function listSprayTasks(query) {
  return request({
    url: '/ppdv/task/spray/list',
    method: 'get',
    params: query
  })
}

// 创建点喷任务
export function createSprayTask(data) {
  return request({
    url: '/ppdv/task/spray',
    method: 'post',
    data: data
  })
}

// 分配执行设备
export function assignSprayTask(id, deviceId) {
  return request({
    url: `/ppdv/task/spray/${id}/assign`,
    method: 'put',
    data: { deviceId }
  })
}

// 下发点喷指令（action=start，duration 秒）
export function executeSprayTask(id, data) {
  return request({
    url: `/ppdv/task/spray/${id}/execute`,
    method: 'put',
    data: data
  })
}

// 更新任务状态
export function updateSprayTaskStatus(id, status) {
  return request({
    url: `/ppdv/task/spray/${id}/status`,
    method: 'put',
    data: { status }
  })
}

// 查询喷后复查列表（按点喷任务分页）
export function listReviewPage(query) {
  return request({
    url: '/ppdv/task/review/list',
    method: 'get',
    params: query
  })
}

// 录入复查结论
export function createReview(sprayTaskId, data) {
  return request({
    url: `/ppdv/task/review/${sprayTaskId}`,
    method: 'post',
    data: data
  })
}
