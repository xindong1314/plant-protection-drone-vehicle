const SUPPORTED_COORDINATE_SYSTEM = 'CGCS2000'

function toCoordinate(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const number = Number(value)
    return Number.isFinite(number) ? number : null
  }
  return null
}

/** 只去掉连续重复顶点；航线首尾闭合是合法路径，不能删。 */
export function normalizePath(points) {
  const result = []
  for (const point of points || []) {
    const last = result[result.length - 1]
    if (last && last[0] === point[0] && last[1] === point[1]) continue
    result.push(point)
  }
  return result
}

/** 去掉连续重复顶点，并移除多边形首尾闭合点。 */
export function normalizePolygon(points) {
  const result = normalizePath(points)
  if (result.length > 1) {
    const first = result[0]
    const last = result[result.length - 1]
    if (first[0] === last[0] && first[1] === last[1]) result.pop()
  }
  return result
}

function parsePoint(vertex) {
  if (!Array.isArray(vertex) || vertex.length !== 2) return null
  const longitude = toCoordinate(vertex[0])
  const latitude = toCoordinate(vertex[1])
  if (longitude === null || latitude === null) return null
  if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) return null
  return [longitude, latitude]
}

/** 严格解析 [[lng,lat], ...]；任何坏点都会让整条边界视为无效，避免坏数据进入 T.Polygon。 */
export function parseBoundaryJson(raw) {
  let parsed
  try {
    parsed = JSON.parse(raw || '[]')
  } catch (_) {
    return []
  }
  if (!Array.isArray(parsed) || parsed.length < 3) return []
  const points = []
  for (const vertex of parsed) {
    const point = parsePoint(vertex)
    if (!point) return []
    points.push(point)
  }
  const clean = normalizePolygon(points)
  return clean.length >= 3 ? clean : []
}

/** 只有 CGCS2000（或历史空标记）的边界才能直接叠加到天地图。 */
export function parseAreaBoundary(area) {
  const coordinateSystem = area?.coordinateSystem
  if (coordinateSystem && coordinateSystem !== SUPPORTED_COORDINATE_SYSTEM) return []
  return parseBoundaryJson(area?.boundaryJson)
}

/** 严格解析路线 [[lng,lat], ...]；任何坏点都会让整条路线视为无效，避免坏数据进入 T.Polyline。 */
export function parseRouteJson(raw) {
  let parsed
  try {
    parsed = JSON.parse(raw || '[]')
  } catch (_) {
    return []
  }
  if (!Array.isArray(parsed)) return []
  const points = []
  for (const vertex of parsed) {
    const point = parsePoint(vertex)
    if (!point) return []
    points.push(point)
  }
  const clean = normalizePath(points)
  return clean.length >= 2 ? clean : []
}

/** 把天地图返回的 LngLat 列表转成 [lng,lat]；closed=true 按多边形去闭环。 */
export function pathToPoints(path, { closed = false } = {}) {
  const points = (path || [])
    .filter(point => point && typeof point.getLng === 'function' && typeof point.getLat === 'function')
    .map(point => [point.getLng(), point.getLat()])
    .filter(([longitude, latitude]) => Number.isFinite(longitude) && Number.isFinite(latitude))
  return closed ? normalizePolygon(points) : normalizePath(points)
}
