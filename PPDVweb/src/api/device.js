import request from '@/utils/request'

export const listDevice = (params) => request({ url: '/ppdv/device/list', method: 'get', params })
export const getDevice = (deviceId) => request({ url: '/ppdv/device/' + deviceId, method: 'get' })
export const stateHistory = (deviceId, limit) =>
  request({ url: `/ppdv/device/${deviceId}/telemetry/state`, method: 'get', params: { limit } })
export const sendCommand = (deviceId, data) =>
  request({ url: `/ppdv/device/${deviceId}/command`, method: 'post', data })
export const assignTask = (deviceId, data) =>
  request({ url: `/ppdv/device/${deviceId}/task`, method: 'post', data })
