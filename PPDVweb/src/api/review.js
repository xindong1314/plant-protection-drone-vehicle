import request from '@/utils/request'

export const listReviewSprayTasks = (params) => request({ url: '/ppdv/task/spray/list', method: 'get', params })
export const listReviewsBySprayTask = (sprayTaskId) => request({ url: `/ppdv/task/review/spray/${sprayTaskId}`, method: 'get' })
export const createReview = (sprayTaskId, data) => request({ url: `/ppdv/task/review/${sprayTaskId}`, method: 'post', data })
export const listReviewAreas = () => request({ url: '/ppdv/area/list', method: 'get' })
