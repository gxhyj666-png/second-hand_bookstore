import request from './request'

// ---------- 认证 ----------
export function login(data) {
  return request.post('/v1/auth/login', data)
}

export function getMe() {
  return request.get('/v1/auth/me')
}

// ---------- 看板 ----------
export function getDashboard() {
  return request.get('/v1/admin/dashboard')
}

// ---------- 教材库 ----------
export function getTextbooks(params) {
  return request.get('/v1/admin/textbooks', { params })
}

export function createTextbook(data) {
  return request.post('/v1/admin/textbooks', data)
}

export function getTextbookCourses(id) {
  return request.get(`/v1/admin/textbooks/${id}/courses`)
}

// ---------- 商品审核 ----------
export function getListings(params) {
  return request.get('/v1/admin/listings', { params })
}

export function approveListing(id) {
  return request.post(`/v1/admin/listings/${id}/approve`)
}

export function rejectListing(id, reason) {
  return request.post(`/v1/admin/listings/${id}/reject`, { reason })
}

// ---------- 订单 ----------
export function getOrders(params) {
  return request.get('/v1/admin/orders', { params })
}

// ---------- 用户 ----------
export function getUsers(params) {
  return request.get('/v1/admin/users', { params })
}

export function freezeUser(id, frozen) {
  return request.post(`/v1/admin/users/${id}/freeze`, { frozen })
}

// ---------- 工单 ----------
export function getTickets(params) {
  return request.get('/v1/admin/tickets', { params })
}

export function resolveTicket(id, data) {
  return request.post(`/v1/admin/tickets/${id}/resolve`, data)
}

// ---------- 接龙 ----------
export function getGroupBuys(params) {
  return request.get('/v1/admin/group-buys', { params })
}
