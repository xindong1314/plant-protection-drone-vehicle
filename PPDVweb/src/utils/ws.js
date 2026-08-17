import { getToken } from '@/utils/auth'

/**
 * 连接遥测 WebSocket，onEvent 收到 { deviceId, kind, ts, data }。
 * 自动重连；返回 { close } 供组件卸载时断开。
 */
export function connectTelemetry(onEvent, lifecycle = {}) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${proto}://${location.host}${import.meta.env.VITE_APP_WS}?token=${getToken()}`
  let ws = null
  let closed = false
  let timer = null

  function open() {
    ws = new WebSocket(url)
    ws.onopen = () => lifecycle.onOpen?.()
    ws.onmessage = (e) => {
      try {
        onEvent(JSON.parse(e.data))
      } catch (_) {
        /* ignore */
      }
    }
    ws.onclose = () => {
      lifecycle.onClose?.()
      if (!closed) {
        timer = setTimeout(open, 3000)
      }
    }
    ws.onerror = () => {
      if (ws) ws.close()
    }
  }

  open()

  return {
    close() {
      closed = true
      if (timer) clearTimeout(timer)
      if (ws) ws.close()
    }
  }
}
