import request from './request'

/** 认证 */
export const authApi = {
  register: (data) => request.post('/v1/auth/register', data),
  login: (data) => request.post('/v1/auth/login', data), // { account, password }
  wechat: (code) => request.post('/v1/auth/wechat', { code }),
  me: () => request.get('/v1/auth/me'),
  updateProfile: (data) => request.put('/v1/auth/profile', data),
  verifyStudent: (data) => request.post('/v1/auth/verify-student', data) // { studentNo, realName }
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
  favorite: (id) => request.post(`/v1/listings/${id}/favorite`),
  favorites: () => request.get('/v1/favorites'),
  track: (data) => request.post('/v1/track', data)
}

/** 订单 */
export const orderApi = {
  create: (data) => request.post('/v1/orders', data), // { listingId, pointId, requestId }
  list: (params) => request.get('/v1/orders', { params }), // { role: buyer|seller, status, page, size }
  detail: (orderNo) => request.get(`/v1/orders/${orderNo}`),
  pay: (orderNo, channel = 'MOCK') => request.post(`/v1/orders/${orderNo}/pay`, { channel }),
  waitVerify: (orderNo) => request.post(`/v1/orders/${orderNo}/wait-verify`),
  cancel: (orderNo) => request.post(`/v1/orders/${orderNo}/cancel`),
  refund: (orderNo, reason) => request.post(`/v1/orders/${orderNo}/refund`, { reason }),
  verifyCode: (orderNo) => request.get(`/v1/orders/${orderNo}/verify-code`),
  verify: (data) => request.post('/v1/orders/verify', data) // { orderNo, code, method }
}

/** 评价与信用 */
export const socialApi = {
  createReview: (data) => request.post('/v1/reviews', data), // { orderNo, rating, content, anonymous }
  userReviews: (userId, params) => request.get(`/v1/users/${userId}/reviews`, { params }),
  userCredit: (userId, params) => request.get(`/v1/users/${userId}/credit-logs`, { params }),
  myCredit: (params) => request.get('/v1/me/credit-logs', { params })
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
