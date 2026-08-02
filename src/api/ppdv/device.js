import request from '@/utils/request'

// 查询设备列表
export function listDevice(query) {
  return request({
    url: '/ppdv/device/list',
    method: 'get',
    params: query
  })
}

// 登记设备（新增）
export function addDevice(data) {
  return request({
    url: '/ppdv/device',
    method: 'post',
    data: data
  })
}

// 删除设备
export function delDevice(ids) {
  return request({
    url: '/ppdv/device/' + ids,
    method: 'delete'
  })
}
