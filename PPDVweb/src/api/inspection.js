import request from '@/utils/request'

export const listInspection = (params) => request({ url: '/ppdv/inspection/list', method: 'get', params })
export const getInspection = (id) => request({ url: `/ppdv/inspection/${id}`, method: 'get' })
export const startInspection = (data) => request({ url: '/ppdv/inspection/start', method: 'post', data })
export const completeInspection = (id) => request({ url: `/ppdv/inspection/${id}/complete`, method: 'put' })
export const uploadInspectionImage = (data) => request({
  url: '/ppdv/inspection/image/upload', method: 'post', data,
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const listImageDetections = (imageId) => request({ url: `/ppdv/inspection/detection/image/${imageId}`, method: 'get' })
export const addDetection = (data) => request({ url: '/ppdv/inspection/detection', method: 'post', data })
export const listAreas = () => request({ url: '/ppdv/area/list', method: 'get' })
