export const money = (n) => `¥${Number(n ?? 0).toFixed(2)}`

export const CONDITION_LABEL = {
  NEW: '全新',
  LIKE_NEW: '几乎全新',
  GOOD: '良好',
  FAIR: '一般',
  POOR: '有笔记'
}

export const CONDITION_OPTIONS = Object.entries(CONDITION_LABEL).map(([value, text]) => ({ value, text }))

export const ORDER_STATUS = {
  PENDING_PAY: { text: '待支付', tone: 'warn' },
  FROZEN: { text: '已支付·待交付', tone: 'info' },
  WAIT_VERIFY: { text: '待核销', tone: 'info' },
  VERIFIED: { text: '已核销', tone: 'ok' },
  SETTLED: { text: '已完成', tone: 'ok' },
  REFUNDED: { text: '已退款', tone: 'muted' },
  CLOSED: { text: '已关闭', tone: 'muted' }
}

export const LISTING_STATUS = {
  ON_SALE: { text: '在售', tone: 'ok' },
  LOCKED: { text: '已锁定', tone: 'warn' },
  SOLD: { text: '已售出', tone: 'muted' },
  OFFLINE: { text: '已下架', tone: 'muted' },
  REJECTED: { text: '审核未过', tone: 'danger' }
}

export const orderStatusText = (s) => ORDER_STATUS[s]?.text || s || '未知'
export const listingStatusText = (s) => LISTING_STATUS[s]?.text || s || '未知'
export const conditionText = (c) => CONDITION_LABEL[c] || c || '未标注'

export function relTime(input) {
  if (!input) return ''
  const t = new Date(String(input).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return String(input)
  const diff = Date.now() - t
  const min = 60 * 1000
  if (diff < min) return '刚刚'
  if (diff < 60 * min) return `${Math.floor(diff / min)} 分钟前`
  if (diff < 24 * 60 * min) return `${Math.floor(diff / (60 * min))} 小时前`
  if (diff < 30 * 24 * 60 * min) return `${Math.floor(diff / (24 * 60 * min))} 天前`
  return shortDate(input)
}

export function shortDate(input) {
  if (!input) return ''
  const d = new Date(String(input).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return String(input)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function remainText(expireAt) {
  if (!expireAt) return ''
  const t = new Date(String(expireAt).replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return ''
  const left = t - Date.now()
  if (left <= 0) return '已超时'
  const m = Math.floor(left / 60000)
  return m >= 60 ? `${Math.floor(m / 60)} 小时 ${m % 60} 分` : `${m} 分钟`
}

/** 生成一次性的幂等键，避免重复提交产生重复订单 */
export function newRequestId() {
  const rnd = Math.random().toString(36).slice(2, 10)
  return `web-${Date.now().toString(36)}-${rnd}`
}

export const maskPhone = (p) => (p ? String(p).replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '')
