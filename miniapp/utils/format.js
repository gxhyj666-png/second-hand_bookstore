/**
 * 展示层格式化工具
 * 品相 / 订单状态 / 时间 / 价格 / 距离 的中文映射与格式化
 */

// 品相枚举（与后端 biz_listing.condition_level 一致）
const CONDITION_MAP = {
  NEW: '全新',
  LIKE_NEW: '9成新',
  GOOD: '8成新',
  FAIR: '有笔记',
  POOR: '品相差'
}

// 品相选择项（卖书页使用）
const CONDITION_LIST = [
  { value: 'NEW', label: '全新', desc: '未拆封或仅翻阅' },
  { value: 'LIKE_NEW', label: '9成新', desc: '无笔记、无划线' },
  { value: 'GOOD', label: '8成新', desc: '少量铅笔笔记' },
  { value: 'FAIR', label: '有笔记', desc: '笔记较多但页面完整' },
  { value: 'POOR', label: '品相差', desc: '有破损或缺页，已注明' }
]

// 订单状态枚举
const ORDER_STATUS_MAP = {
  PENDING_PAY: '待支付',
  FROZEN: '已支付·冻结',
  WAIT_VERIFY: '待核销',
  VERIFIED: '已核销',
  SETTLED: '已结算',
  REFUNDED: '已退款',
  CLOSED: '已关闭'
}

// 订单状态对应的标签色
const ORDER_STATUS_TONE = {
  PENDING_PAY: 'gold',
  FROZEN: 'blue',
  WAIT_VERIFY: 'green',
  VERIFIED: 'green',
  SETTLED: 'gray',
  REFUNDED: 'red',
  CLOSED: 'gray'
}

// 商品状态
const LISTING_STATUS_MAP = {
  ON_SALE: '在售',
  LOCKED: '待核销',
  SOLD: '已售出',
  OFFLINE: '已下架',
  REJECTED: '未通过'
}

// 课程-教材绑定类型
const BIND_TYPE_MAP = {
  REQUIRED: '本校指定',
  REFERENCE: '参考书'
}

// 搜索排序项（与 GET /v1/listings 的 sort 参数一致）
const SORT_LIST = [
  { value: 'recommend', label: '综合排序' },
  { value: 'latest', label: '最新上架' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
  { value: 'distance', label: '距离最近' }
]

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

/**
 * 兼容字符串 / 时间戳 / Date / ISO 字符串的解析
 * iOS 不认 '2026-11-27 09:00:00'，统一替换成 '/' 再解析
 */
function toDate(input) {
  if (!input) return null
  if (input instanceof Date) return isNaN(input.getTime()) ? null : input
  if (typeof input === 'number') {
    const d = new Date(input)
    return isNaN(d.getTime()) ? null : d
  }
  let str = String(input).trim()
  if (!str) return null
  if (/^\d{10,13}$/.test(str)) {
    const d = new Date(Number(str.length === 10 ? str + '000' : str))
    return isNaN(d.getTime()) ? null : d
  }
  // 带时区（Z / +08:00）时交给原生解析
  if (/(Z|[+-]\d{2}:?\d{2})$/.test(str)) {
    const d = new Date(str)
    return isNaN(d.getTime()) ? null : d
  }
  str = str.replace('T', ' ').replace(/\.\d+$/, '')
  const d = new Date(str.replace(/-/g, '/'))
  return isNaN(d.getTime()) ? null : d
}

/**
 * 时间格式化
 * @param {*} input
 * @param {String} pattern 支持 YYYY MM DD HH mm ss
 */
function formatTime(input, pattern) {
  const d = toDate(input)
  if (!d) return ''
  const fmt = pattern || 'YYYY-MM-DD HH:mm'
  return fmt
    .replace('YYYY', d.getFullYear())
    .replace('MM', pad2(d.getMonth() + 1))
    .replace('DD', pad2(d.getDate()))
    .replace('HH', pad2(d.getHours()))
    .replace('mm', pad2(d.getMinutes()))
    .replace('ss', pad2(d.getSeconds()))
}

/**
 * 相对时间：刚刚 / 12 分钟前 / 3 小时前 / 昨天 / 11-10
 */
function relativeTime(input) {
  const d = toDate(input)
  if (!d) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 24 * 60 * 60 * 1000) return Math.floor(diff / 3600000) + ' 小时前'
  if (diff < 48 * 60 * 60 * 1000) return '昨天'
  return formatTime(d, 'MM-DD')
}

/**
 * 距离文案：0.4km / 850m
 */
function distanceText(km) {
  if (km === null || km === undefined || km === '') return ''
  const n = Number(km)
  if (isNaN(n)) return ''
  if (n < 1) return Math.round(n * 1000) + 'm'
  return n.toFixed(1) + 'km'
}

/**
 * 价格格式化：15 → 15.00
 */
function formatPrice(value) {
  const n = Number(value)
  if (isNaN(n) || n < 0) return '0.00'
  return n.toFixed(2)
}

/**
 * 折扣百分比：15 / 45 → 67（省 67%）
 */
function discountPercent(price, originalPrice) {
  const p = Number(price)
  const o = Number(originalPrice)
  if (!p || !o || o <= p) return 0
  return Math.round((1 - p / o) * 100)
}

/**
 * 秒数 → 00:29
 */
function countdownText(seconds) {
  let s = Number(seconds)
  if (isNaN(s) || s < 0) s = 0
  const m = Math.floor(s / 60)
  const sec = s % 60
  return pad2(m) + ':' + pad2(sec)
}

function conditionText(value) {
  return CONDITION_MAP[value] || '品相未知'
}

function conditionDesc(value) {
  const hit = CONDITION_LIST.filter(function (item) {
    return item.value === value
  })[0]
  return hit ? hit.desc : ''
}

function orderStatusText(status) {
  return ORDER_STATUS_MAP[status] || '未知状态'
}

function orderStatusTone(status) {
  return ORDER_STATUS_TONE[status] || 'gray'
}

function listingStatusText(status) {
  return LISTING_STATUS_MAP[status] || '未知'
}

function bindTypeText(bindType) {
  return BIND_TYPE_MAP[bindType] || '教材'
}

module.exports = {
  CONDITION_MAP: CONDITION_MAP,
  CONDITION_LIST: CONDITION_LIST,
  ORDER_STATUS_MAP: ORDER_STATUS_MAP,
  ORDER_STATUS_TONE: ORDER_STATUS_TONE,
  LISTING_STATUS_MAP: LISTING_STATUS_MAP,
  BIND_TYPE_MAP: BIND_TYPE_MAP,
  SORT_LIST: SORT_LIST,
  toDate: toDate,
  formatTime: formatTime,
  relativeTime: relativeTime,
  distanceText: distanceText,
  formatPrice: formatPrice,
  discountPercent: discountPercent,
  countdownText: countdownText,
  conditionText: conditionText,
  conditionDesc: conditionDesc,
  orderStatusText: orderStatusText,
  orderStatusTone: orderStatusTone,
  listingStatusText: listingStatusText,
  bindTypeText: bindTypeText
}
