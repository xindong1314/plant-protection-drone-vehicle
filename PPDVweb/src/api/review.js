import request from '@/utils/request'

export const listReviewPage = (params) => request({ url: '/ppdv/task/review/list', method: 'get', params })
export const createReview = (sprayTaskId, data) => request({ url: `/ppdv/task/review/${sprayTaskId}`, method: 'post', data })
export const listReviewAreas = () => request({ url: '/ppdv/area/list', method: 'get' })
