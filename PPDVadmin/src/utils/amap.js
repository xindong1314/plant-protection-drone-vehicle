import AMapLoader from '@amap/amap-jsapi-loader'

export const AMAP_KEY = import.meta.env.VITE_AMAP_KEY
const SECURITY = import.meta.env.VITE_AMAP_SECURITY

let loaderPromise = null

/**
 * 加载高德地图 JS API 2.0（含常用插件）。未配置 Key 或 JS API 加载失败时 reject，失败后可重新调用重试。
 */
export function loadAMap() {
  if (!AMAP_KEY) {
    return Promise.reject(new Error('未配置 VITE_AMAP_KEY，请在 .env 中配置高德地图 Key'))
  }
  if (SECURITY) {
    window._AMapSecurityConfig = { securityJsCode: SECURITY }
  }
  if (!loaderPromise) {
    loaderPromise = AMapLoader.load({
      key: AMAP_KEY,
      version: '2.0',
      plugins: ['AMap.MouseTool', 'AMap.PolygonEditor', 'AMap.PolylineEditor', 'AMap.Scale', 'AMap.ToolBar']
    }).catch(error => {
      loaderPromise = null
      throw new Error(`高德地图 JS API 加载失败，请检查网络或 Key/安全码配置：${error?.message || error}`)
    })
  }
  return loaderPromise
}
