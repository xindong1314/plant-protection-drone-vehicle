import request from '@/utils/request'

export const listRiskConfigs = () => request({ url: '/ppdv/risk/config/list', method: 'get' })
export const updateRiskConfig = (id, configValue) => request({
  url: `/ppdv/risk/config/${id}`, method: 'put', data: { configValue }
})
export const evaluateAreaRisk = (areaId) => request({ url: `/ppdv/risk/evaluate/${areaId}`, method: 'post' })
export const listRiskAreas = () => request({ url: '/ppdv/area/list', method: 'get' })
