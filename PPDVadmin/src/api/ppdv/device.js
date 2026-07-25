import request from '@/utils/request'

// 查询设备列表
export function listDevice(query) {
  return request({
    url: '/ppdv/device/list',
    method: 'get',
    params: query
  })
}

// 查询设备详情
export function getDevice(deviceId) {
  return request({
    url: '/ppdv/device/' + deviceId,
    method: 'get'
  })
}

// 登记设备
export function addDevice(data) {
  return request({
    url: '/ppdv/device',
    method: 'post',
    data: data
  })
}

// 删除设备
export function delDevice(ids) {
  return request({
    url: '/ppdv/device/' + ids,
    method: 'delete'
  })
}

// 下发指令
export function sendCommand(deviceId, data) {
  return request({
    url: `/ppdv/device/${deviceId}/command`,
    method: 'post',
    data: data
  })
}

// 重置设备密钥
export function resetSecret(deviceId) {
  return request({
    url: `/ppdv/device/${deviceId}/resetSecret`,
    method: 'put'
  })
}

// 状态遥测历史
export function stateHistory(deviceId, limit) {
  return request({
    url: `/ppdv/device/${deviceId}/telemetry/state`,
    method: 'get',
    params: { limit }
  })
}
