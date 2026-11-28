import request from './request'

/** 认证 */
export const authApi = {
  register: (data) => request.post('/v1/auth/register', data),
  login: (data) => request.post('/v1/auth/login', data), // { account, password }
  smsCode: (data) => request.post('/v1/auth/sms-code', data), // { phone, purpose? 'LOGIN' | 'BIND_PHONE' }
  smsLogin: (data) => request.post('/v1/auth/sms-login', data), // { phone, code }
  wechat: (code) => request.post('/v1/auth/wechat', { code }),
  me: () => request.get('/v1/auth/me'),
  updateProfile: (data) => request.put('/v1/auth/profile', data),
  setPassword: (data) => request.post('/v1/auth/password', data), // { oldPassword, newPassword }
  verifyStudent: (data) => request.post('/v1/auth/verify-student', data) // { studentNo, realName }
}

/** 实名 / 学生 / 人脸认证 */
export const verifyApi = {
  status: () => request.get('/v1/verify/status'),
  idCard: (data) => request.post('/v1/verify/id-card', data), // { realName, idNo, frontUrl, backUrl }
  student: (data) => request.post('/v1/verify/student', data), // { schoolName, studentNo, coverUrl, infoUrl }
  face: (data) => request.post('/v1/verify/face', data) // { imageUrl }
}

/** ISBN 查询 */
export const isbnApi = {
  lookup: (isbn) => request.get(`/v1/isbn/${encodeURIComponent(isbn)}`),
  /** 拍照识别书背 EAN-13 条码（后端本地 ZXing 解码 + 第三方图书库补书名） */
  scan: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/v1/isbn/scan', form, { timeout: 40000 })
  }
}

/** 课程与教材 */
export const catalogApi = {
  courses: (params) => request.get('/v1/courses', { params }),
  course: (id) => request.get(`/v1/courses/${id}`),
  courseTextbooks: (id) => request.get(`/v1/courses/${id}/textbooks`),
  textbooks: (params) => request.get('/v1/textbooks', { params }),
  textbook: (id) => request.get(`/v1/textbooks/${id}`),
  priceIndex: (id) => request.get(`/v1/textbooks/${id}/price-index`),
  recommend: (limit = 8) => request.get('/v1/recommend', { params: { limit } })
}

/** 商品 */
export const listingApi = {
  publish: (data) => request.post('/v1/listings', data),
  search: (params) => request.get('/v1/listings', { params }),
  mine: (params) => request.get('/v1/listings/mine', { params }),
  detail: (id) => request.get(`/v1/listings/${id}`),
  update: (id, data) => request.put(`/v1/listings/${id}`, data),
  offline: (id) => request.post(`/v1/listings/${id}/offline`),
  online: (id) => request.post(`/v1/listings/${id}/online`),
  favorite: (id) => request.post(`/v1/listings/${id}/favorite`),
  favorites: () => request.get('/v1/favorites'),
  track: (data) => request.post('/v1/track', data),
  photos: (id) => request.get(`/v1/listings/${id}/photos`),
  savePhotos: (id, photos) => request.post(`/v1/listings/${id}/photos`, { photos }) // [{ type, url }]
}

/** 订单（PENDING_PAY → WAIT_VERIFY → COMPLETED，另有退款/介入/关闭） */
export const orderApi = {
  create: (data) => request.post('/v1/orders', data), // { listingId, pointId, requestId }
  list: (params) => request.get('/v1/orders', { params }), // { role: buyer|seller, status, page, size }
  detail: (orderNo) => request.get(`/v1/orders/${orderNo}`),
  refunds: (orderNo) => request.get(`/v1/orders/${orderNo}/refunds`),
  pay: (orderNo, channel = 'MOCK') => request.post(`/v1/orders/${orderNo}/pay`, { channel }),
  cancel: (orderNo) => request.post(`/v1/orders/${orderNo}/cancel`),
  verifyCode: (orderNo) => request.get(`/v1/orders/${orderNo}/verify-code`),
  regenerateVerifyCode: (orderNo) => request.post(`/v1/orders/${orderNo}/verify-code/regenerate`),
  verify: (orderNo, data) => request.post(`/v1/orders/${orderNo}/verify`, data), // 卖家 { code, signPhotoUrl }
  meets: (orderNo) => request.get(`/v1/orders/${orderNo}/meets`),
  createMeet: (orderNo, data) => request.post(`/v1/orders/${orderNo}/meets`, data), // { type, place, time }
  confirmMeet: (meetId, data) => request.post(`/v1/orders/meets/${meetId}/confirm`, data), // { agreed, note }
  refund: (orderNo, data) => request.post(`/v1/orders/${orderNo}/refund`, data), // { reason, photoUrl, refundType }
  agreeRefund: (refundId) => request.post(`/v1/orders/refunds/${refundId}/agree`),
  rejectRefund: (refundId, data) => request.post(`/v1/orders/refunds/${refundId}/reject`, data), // { reason }
  finishRefund: (refundId) => request.post(`/v1/orders/refunds/${refundId}/finish`),
  arbitrateRefund: (refundId, data) => request.post(`/v1/orders/refunds/${refundId}/arbitrate`, data), // { note }
  reportNoShow: (orderNo, data) => request.post(`/v1/orders/${orderNo}/no-show`, data) // { note }
}

/** 私信 */
export const conversationApi = {
  create: (listingId) => request.post('/v1/conversations', { listingId }),
  list: (params) => request.get('/v1/conversations', { params }),
  messages: (id, afterId = 0) => request.get(`/v1/conversations/${id}/messages`, { params: { afterId } }),
  send: (id, data) => request.post(`/v1/conversations/${id}/messages`, data) // { type, content, extra }
}

/** 商户端 */
export const merchantApi = {
  apply: (data) => request.post('/v1/merchant/apply', data), // { shopName, licenseUrl, contact }
  my: () => request.get('/v1/merchant/my'),
  stats: () => request.get('/v1/merchant/stats'),
  batch: (listings) => request.post('/v1/merchant/listings/batch', { listings }),
  offline: (listingIds) => request.post('/v1/merchant/listings/offline', { listingIds }),
  // 商户工作台
  dashboard: () => request.get('/v1/merchant/dashboard'),
  listings: (params) => request.get('/v1/merchant/listings', { params }),
  toggle: (id, online) => request.post(`/v1/merchant/listings/${id}/toggle`, { online }),
  price: (listingIds, value, mode) => request.post('/v1/merchant/listings/price', { listingIds, value, mode }),
  orders: (params) => request.get('/v1/merchant/orders', { params }),
  settlements: (params) => request.get('/v1/merchant/settlements', { params })
}

/** 店铺主页（游客可看） */
export const shopApi = {
  get: (merchantId, params) => request.get(`/v1/shops/${merchantId}`, { params })
}

/** 评价与信用 */
export const socialApi = {
  createReview: (data) => request.post('/v1/reviews', data), // { orderNo, rating, content, anonymous }
  userReviews: (userId, params) => request.get(`/v1/users/${userId}/reviews`, { params }),
  userCredit: (userId, params) => request.get(`/v1/users/${userId}/credit-logs`, { params }),
  myCredit: (params) => request.get('/v1/me/credit-logs', { params })
}

/** 个人中心：我的数据 / 我的评价 / 换绑手机号 */
export const meApi = {
  stats: () => request.get('/v1/my/stats'),
  reviews: (params) => request.get('/v1/my/reviews', { params }), // { type: received|sent, page, size }
  changePhone: (data) => request.put('/v1/me/phone', data) // { phone, code }
}

/** 公开用户主页（他人资料 + 在售 + 评价） */
export const userApi = {
  profile: (id) => request.get(`/v1/users/${id}/profile`)
}

/** 字典：学院 / 专业 / 年级 */
export const metaApi = {
  profileOptions: () => request.get('/v1/meta/profile-options')
}

/** 工单 */
export const ticketApi = {
  create: (data) => request.post('/v1/tickets', data),
  mine: (params) => request.get('/v1/tickets/mine', { params }),
  messages: (id) => request.get(`/v1/tickets/${id}/messages`),
  reply: (id, content) => request.post(`/v1/tickets/${id}/messages`, { content })
}

/** 班级接龙 */
export const groupBuyApi = {
  create: (data) => request.post('/v1/group-buys', data),
  list: (params) => request.get('/v1/group-buys', { params }),
  detail: (inviteCode) => request.get(`/v1/group-buys/${inviteCode}`),
  join: (inviteCode, data) => request.post(`/v1/group-buys/${inviteCode}/join`, data),
  close: (inviteCode) => request.post(`/v1/group-buys/${inviteCode}/close`)
}

/** 自提点 */
export const pickupApi = {
  list: (params) => request.get('/v1/pickup-points', { params }),
  get: (id) => request.get(`/v1/pickup-points/${id}`),
  mine: () => request.get('/v1/pickup-points/mine')
}

/** 通知 */
export const noticeApi = {
  page: (params) => request.get('/v1/notices', { params }),
  unreadCount: () => request.get('/v1/notices/unread-count'),
  read: (id) => request.post(`/v1/notices/${id}/read`),
  readAll: () => request.post('/v1/notices/read-all')
}

export const healthApi = () => request.get('/v1/health')

/** 存储：图片与视频统一上传，返回可访问 URL */
export const fileApi = {
  upload: (file, kind = 'image') => {
    const form = new FormData()
    form.append('file', file)
    form.append('kind', kind)
    return request.post('/v1/files/upload', form)
  }
}

/** 进阶：个体 → 商户 / 运营 */
export const upgradeApi = {
  redeem: (data) => request.post('/v1/upgrade/redeem', data) // { code, shopName, contact }
}

/** 运营端 */
export const operatorApi = {
  overview: () => request.get('/v1/operator/overview'),
  invitees: (params) => request.get('/v1/operator/invitees', { params }),
  commissions: (params) => request.get('/v1/operator/commissions', { params }),
  complaints: (params) => request.get('/v1/operator/complaints', { params }),
  handleComplaint: (id, data) => request.post(`/v1/operator/complaints/${id}/handle`, data),
  offlineVerifies: (params) => request.get('/v1/operator/offline-verifies', { params }),
  reviewOffline: (id, data) => request.post(`/v1/operator/offline-verifies/${id}/review`, data)
}

/** 推广关系绑定（普通用户填运营推广码） */
export const promotionApi = {
  bind: (promoCode) => request.post('/v1/promotion/bind', { promoCode })
}

/** 线下认证材料提交 */
export const offlineVerifyApi = {
  apply: (data) => request.post('/v1/verify/offline', data) // { verifyType, materialUrl, note }
}
