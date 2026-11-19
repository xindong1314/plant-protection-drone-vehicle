import request from '@/utils/request'

export const listTrackSessions = (params) => request({ url: '/ppdv/track/sessions', method: 'get', params })
export const getTrackSession = (id) => request({ url: `/ppdv/track/sessions/${id}`, method: 'get' })
export const listTrackClasses = () => request({ url: '/ppdv/track/classes', method: 'get' })
