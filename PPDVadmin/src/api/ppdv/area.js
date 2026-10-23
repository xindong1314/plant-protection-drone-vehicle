import request from '@/utils/request'

// 查询区域列表
export function listArea() {
  return request({
    url: '/ppdv/area/list',
    method: 'get'
  })
}

// 查询区域详细
export function getArea(id) {
  return request({
    url: '/ppdv/area/' + id,
    method: 'get'
  })
}

// 新增区域
export function addArea(data) {
  return request({
    url: '/ppdv/area',
    method: 'post',
    data: data
  })
}

// 修改区域
export function updateArea(data) {
  return request({
    url: '/ppdv/area',
    method: 'put',
    data: data
  })
}

// 删除区域
export function delArea(id) {
  return request({
    url: '/ppdv/area/' + id,
    method: 'delete'
  })
}
