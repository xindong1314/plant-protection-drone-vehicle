import request from '@/utils/request'

export const listArea = () => request({ url: '/ppdv/area/list', method: 'get' })
export const addArea = (data) => request({ url: '/ppdv/area', method: 'post', data })
export const updateArea = (data) => request({ url: '/ppdv/area', method: 'put', data })
export const deleteArea = (id) => request({ url: `/ppdv/area/${id}`, method: 'delete' })
