import { defineStore } from 'pinia'
import { connectTelemetry } from '@/utils/ws'

// ---------- WebSocket 实例（模块级，非响应式） ----------
let connection = null

export const useTelemetryStore = defineStore('telemetry', {
  state: () => ({
    devices: {},       // { 'PPDV-0001': { status, battery, mode, lat, lon, speed, spraying, flow, tankLevel, currentZone, lastSeenTs } }
    alarms: [],        // [{ deviceId, code, level, msg, ts }]
    events: [],        // [{ deviceId, kind, ts, summary }]  环形缓冲，最多 50 条
    wsConnected: false,
  }),

  getters: {
    onlineCount:    (state) => Object.values(state.devices).filter(d => d.status === 'ONLINE').length,
    totalCount:     (state) => Object.keys(state.devices).length,
    alarmCount:     (state) => state.alarms.length,
    sprayingCount:  (state) => Object.values(state.devices).filter(d => d.spraying).length,
    deviceList:     (state) => Object.values(state.devices),
    recentEvents:   (state) => state.events.slice(0, 20),
    recentAlarms:   (state) => state.alarms.slice(0, 10),
  },

  actions: {
    // ---------- 连接 WebSocket ----------
    connect() {
      connection?.close()
      connection = connectTelemetry(
        (message) => this.handleMessage(message),
        {
          onOpen: () => { this.wsConnected = true },
          onClose: () => { this.wsConnected = false },
        }
      )
    },

    // ---------- 断开 ----------
    disconnect() {
      connection?.close()
      connection = null
      this.devices = {}
      this.alarms = []
      this.events = []
      this.wsConnected = false
    },

    // ---------- 从 REST 接口初始化设备列表 ----------
    initDevices(rows) {
      const map = {}
      rows.forEach(row => {
        map[row.deviceId] = {
          deviceId: row.deviceId,
          status: row.status || 'OFFLINE',
          battery: row.battery,
          mode: row.mode,
          lat: row.lastLat,
          lon: row.lastLon,
          currentZone: row.currentZone,
          spraying: false,
          flow: null,
          tankLevel: null,
          speed: null,
          lastSeenTs: row.lastSeenAt ? new Date(row.lastSeenAt).getTime() : null,
        }
      })
      this.devices = map
    },

    // ---------- 处理 WebSocket 消息 ----------
    handleMessage(msg) {
      const { deviceId, kind, ts, data } = msg

      // 确保设备存在
      if (!this.devices[deviceId]) {
        this.devices[deviceId] = { deviceId, status: 'OFFLINE' }
      }
      const dev = this.devices[deviceId]

      let summary = ''
      switch (kind) {
        case 'status':
          dev.status = data.online ? 'ONLINE' : 'OFFLINE'
          dev.lastSeenTs = ts
          summary = data.online ? '上线' : '离线'
          break

        case 'tele/state':
          dev.battery = data.battery
          dev.mode = data.mode
          dev.lat = data.lat
          dev.lon = data.lon
          dev.speed = data.speed
          dev.lastSeenTs = ts
          dev.status = 'ONLINE'
          summary = `电量 ${data.battery ?? '?'}% 模式 ${data.mode ?? '?'}`
          break

        case 'tele/work':
          dev.spraying = data.spraying
          dev.flow = data.flow
          dev.tankLevel = data.tankLevel
          dev.lastSeenTs = ts
          dev.status = 'ONLINE'
          summary = data.spraying ? `喷洒中 流量 ${data.flow ?? '?'}L/min` : '停止喷洒'
          break

        case 'tele/zone':
          dev.currentZone = data.zoneCode
          dev.lastSeenTs = ts
          dev.status = 'ONLINE'
          summary = `进入区域 ${data.zoneCode ?? '?'}`
          break

        case 'event/alarm':
          this.alarms.unshift({ deviceId, code: data.code, level: data.level, msg: data.msg, ts })
          summary = `⚠️ ${data.code} ${data.msg}`
          break

        case 'task/progress':
          summary = `任务进度 ${data.progress ?? '?'}%`
          break

        default:
          summary = JSON.stringify(data).slice(0, 60)
      }

      // 追加事件（环形缓冲，最多 50 条）
      this.events.unshift({ deviceId, kind, ts, summary })
      if (this.events.length > 50) {
        this.events.pop()
      }
    },
  },
})
