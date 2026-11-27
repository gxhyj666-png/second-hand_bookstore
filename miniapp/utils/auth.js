/**
 * 登录态与鉴权
 * token 存在 Storage，key = 'token'；用户信息缓存 key = 'userInfo'
 */
const { request } = require('./request')

const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || ''
  } catch (e) {
    return ''
  }
}

function setToken(token) {
  try {
    if (token) wx.setStorageSync(TOKEN_KEY, token)
    else wx.removeStorageSync(TOKEN_KEY)
  } catch (e) {
    // ignore
  }
}

function getUser() {
  try {
    return wx.getStorageSync(USER_KEY) || null
  } catch (e) {
    return null
  }
}

function setUser(user) {
  try {
    if (user) wx.setStorageSync(USER_KEY, user)
    else wx.removeStorageSync(USER_KEY)
  } catch (e) {
    // ignore
  }
}

function isLoggedIn() {
  return !!getToken()
}

/**
 * 登录
 * @param {String} account 手机号或账号（演示账号 buyer001）
 * @param {String} password
 */
function login(account, password) {
  return request('/v1/auth/login', {
    method: 'POST',
    data: { account: account, password: password },
    loading: true,
    loadingText: '登录中'
  }).then(function (data) {
    if (!data || !data.token) throw new Error('登录返回缺少 token')
    setToken(data.token)
    if (data.user) setUser(data.user)
    return data
  })
}

/**
 * 注册
 * @param {Object} payload { phone, password, nickname }
 */
function register(payload) {
  return request('/v1/auth/register', {
    method: 'POST',
    data: payload,
    loading: true,
    loadingText: '注册中'
  }).then(function (data) {
    if (data && data.token) setToken(data.token)
    if (data && data.user) setUser(data.user)
    return data
  })
}

/**
 * 当前用户（静默调用，失败不弹提示）
 */
function fetchMe() {
  return request('/v1/auth/me', { silent: true })
}

function clearLogin() {
  setToken('')
  setUser(null)
}

function logout() {
  clearLogin()
}

/**
 * 页面内守卫：未登录时跳登录页
 * @returns {Boolean} 是否已登录
 */
function requireLogin() {
  if (isLoggedIn()) return true
  wx.navigateTo({ url: '/pages/login/index' })
  return false
}

module.exports = {
  TOKEN_KEY: TOKEN_KEY,
  getToken: getToken,
  setToken: setToken,
  getUser: getUser,
  setUser: setUser,
  isLoggedIn: isLoggedIn,
  login: login,
  register: register,
  fetchMe: fetchMe,
  clearLogin: clearLogin,
  logout: logout,
  requireLogin: requireLogin
}
