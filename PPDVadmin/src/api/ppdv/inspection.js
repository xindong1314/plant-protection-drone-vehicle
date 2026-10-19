import request from '@/utils/request'

// 查询巡检记录列表（分页）
export function listInspection(query) {
  return request({
    url: '/ppdv/inspection/list',
    method: 'get',
    params: query
  })
}

// 查询巡检记录详情（顶层返回 record + images）
export function getInspection(id) {
  return request({
    url: '/ppdv/inspection/' + id,
    method: 'get'
  })
}

// 查询图片的识别结果
export function listImageDetections(imageId) {
  return request({
    url: '/ppdv/inspection/detection/image/' + imageId,
    method: 'get'
  })
}
