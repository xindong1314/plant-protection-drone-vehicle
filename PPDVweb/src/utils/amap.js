import AMapLoader from '@amap/amap-jsapi-loader'

export const AMAP_KEY = import.meta.env.VITE_AMAP_KEY
const SECURITY = import.meta.env.VITE_AMAP_SECURITY

let loaderPromise = null

/**
 * 加载高德地图 JS API 2.0（含常用插件）。未配置 Key 时 reject，调用方需降级提示。
 */
export function loadAMap() {
  if (!AMAP_KEY) {
    return Promise.reject(new Error('未配置高德地图 Key，请在 .env 设置 VITE_AMAP_KEY'))
  }
  if (SECURITY) {
    window._AMapSecurityConfig = { securityJsCode: SECURITY }
  }
  if (!loaderPromise) {
    loaderPromise = AMapLoader.load({
      key: AMAP_KEY,
      version: '2.0',
      plugins: ['AMap.MouseTool', 'AMap.PolygonEditor', 'AMap.PolylineEditor', 'AMap.Scale', 'AMap.ToolBar']
    })
  }
  return loaderPromise
}
