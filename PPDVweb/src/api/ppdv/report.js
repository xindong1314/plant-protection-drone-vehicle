import request from '@/utils/request'

// 获取数据报表（可选时间范围筛选）
export function getReport(startTime, endTime) {
  return request({
    url: '/ppdv/report',
    method: 'get',
    params: { startTime, endTime }
  })
}
