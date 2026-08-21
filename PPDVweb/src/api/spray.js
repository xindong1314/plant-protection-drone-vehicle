import request from '@/utils/request'

export const listSprayTasks = (params) => request({ url: '/ppdv/task/spray/list', method: 'get', params })
export const createSprayTask = (data) => request({ url: '/ppdv/task/spray', method: 'post', data })
export const assignSprayTask = (id, deviceId) => request({ url: `/ppdv/task/spray/${id}/assign`, method: 'put', data: { deviceId } })
export const executeSprayTask = (id, data) => request({ url: `/ppdv/task/spray/${id}/execute`, method: 'put', data })
export const updateSprayTaskStatus = (id, status) => request({ url: `/ppdv/task/spray/${id}/status`, method: 'put', data: { status } })
export const listSprayAreas = () => request({ url: '/ppdv/area/list', method: 'get' })
