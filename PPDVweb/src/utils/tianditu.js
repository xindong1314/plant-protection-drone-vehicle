const TIANDITU_KEY = import.meta.env.VITE_TIANDITU_KEY
// 主脚本 onload 后，绘制工具、覆盖物和控件类由组件包异步注入，需等调用方会用到的类齐全
const READY_CHECK_MS = 50
const READY_TIMEOUT_MS = 10000

let loaderPromise = null

function tiandituReady() {
  const T = window.T
  return !!(
    T &&
    T.Map &&
    T.LngLat &&
    T.Control?.MapType &&
    T.Polygon &&
    T.Polyline &&
    T.PolygonTool &&
    T.PolylineTool
  )
}

function waitForComponents() {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now()
    const timer = setInterval(() => {
      if (tiandituReady()) {
        clearInterval(timer)
        resolve(window.T)
        return
      }
      if (Date.now() - startedAt > READY_TIMEOUT_MS) {
        clearInterval(timer)
        reject(new Error('天地图组件包加载超时，请刷新重试'))
      }
    }, READY_CHECK_MS)
  })
}

function injectScript() {
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://api.tianditu.gov.cn/api?v=4.0&tk=${TIANDITU_KEY}`
    script.onload = () => resolve(waitForComponents())
    script.onerror = () => {
      script.remove()
      reject(new Error('天地图脚本加载失败，请检查网络或 Key'))
    }
    document.head.appendChild(script)
  })
}

/**
 * 动态加载天地图 JavaScript API 4.0。主脚本 onload 后 Map/Polygon/Polyline/PolygonTool/
 * PolylineTool/Control.MapType 仍由组件包异步注入，需全部就绪再 resolve，否则调用方
 * 初始化会抛错并被降级遮罩覆盖。未配置 Key 时 reject，调用方需降级提示。
 */
export function loadTianditu() {
  if (!TIANDITU_KEY) {
    return Promise.reject(new Error('未配置天地图 Key，请到 lbs.tianditu.gov.cn 免费申请后填入 VITE_TIANDITU_KEY'))
  }
  if (tiandituReady()) return Promise.resolve(window.T)
  if (!loaderPromise) {
    loaderPromise = (window.T ? waitForComponents() : injectScript()).catch(error => {
      loaderPromise = null
      throw error
    })
  }
  return loaderPromise
}
