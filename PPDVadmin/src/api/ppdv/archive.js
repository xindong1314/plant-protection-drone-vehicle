import request from '@/utils/request'

// 查询区域档案汇总（区域 + 档案合并数据，可按 keyword/riskStatus 过滤）
export function listAreaArchives(query) {
  return request({
    url: '/ppdv/archive/map',
    method: 'get',
    params: query
  })
}

// 查询区域档案详情
export function getAreaArchive(areaId) {
  return request({
    url: '/ppdv/archive/' + areaId,
    method: 'get'
  })
}
