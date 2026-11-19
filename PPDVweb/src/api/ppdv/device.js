import request from '@/utils/request'

// 查询设备列表
export function listDevice(query) {
  return request({
    url: '/ppdv/device/list',
    method: 'get',
    params: query
  })
}

// 查询设备遥测历史（状态）
export function getStateHistory(deviceId, limit = 20) {
  return request({
    url: `/ppdv/device/${deviceId}/telemetry/state`,
    method: 'get',
    params: { limit }
  })
}
