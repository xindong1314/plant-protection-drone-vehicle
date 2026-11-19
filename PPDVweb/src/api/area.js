import request from '@/utils/request'

export const listArea = () => request({ url: '/ppdv/area/list', method: 'get' })
export const addArea = (data) => request({ url: '/ppdv/area', method: 'post', data })
export const updateArea = (data) => request({ url: '/ppdv/area', method: 'put', data })
export const deleteArea = (id) => request({ url: `/ppdv/area/${id}`, method: 'delete' })
// v1.1 评审 M1：列表不再下发 routeJson，需要完整路线时按 id 拉取
export const getAreaRoute = (id) => request({ url: `/ppdv/area/${id}/route`, method: 'get' })
// 评审修复#11：区域页地图一次批量拉取全部已录路线，替代逐区域的并行 N+1 请求
export const listAreaRoutes = () => request({ url: '/ppdv/area/routes', method: 'get' })
export const startRouteRecording = (id, deviceId) =>
  request({ url: `/ppdv/area/${id}/route/recording`, method: 'post', data: { deviceId } })
export const stopRouteRecording = (id) => request({ url: `/ppdv/area/${id}/route/recording/stop`, method: 'post' })
export const cancelRouteRecording = (id) => request({ url: `/ppdv/area/${id}/route/recording`, method: 'delete' })
export const listRouteRecordings = () => request({ url: '/ppdv/area/route/recording', method: 'get' })
