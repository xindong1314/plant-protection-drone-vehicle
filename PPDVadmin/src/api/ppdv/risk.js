import request from '@/utils/request'

// 查询风险规则配置（按类别分组返回）
export function listRiskConfigs() {
  return request({
    url: '/ppdv/risk/config/list',
    method: 'get'
  })
}

// 修改风险规则配置
export function updateRiskConfig(id, configValue) {
  return request({
    url: '/ppdv/risk/config/' + id,
    method: 'put',
    data: { configValue }
  })
}

// 重新计算区域风险
export function evaluateAreaRisk(areaId) {
  return request({
    url: '/ppdv/risk/evaluate/' + areaId,
    method: 'post'
  })
}
