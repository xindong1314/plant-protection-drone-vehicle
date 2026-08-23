import request from '@/utils/request'

export const listAreaArchives = () => request({ url: '/ppdv/archive/map', method: 'get' })
export const getAreaArchive = (areaId) => request({ url: `/ppdv/archive/${areaId}`, method: 'get' })
