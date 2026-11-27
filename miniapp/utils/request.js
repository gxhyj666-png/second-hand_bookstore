/**
 * 网络请求封装
 * - 统一拼接 baseUrl、添加 Authorization: Bearer <token>
 * - 统一拆 { code, message, data } 三层结构，code === 0 时直接把 data 交给页面
 * - 401 清理登录态并跳转登录页
 * - 可选 loading / 静默（silent）模式
 * - USE_MOCK 打开时请求打到 utils/mock.js 的本地假接口，不发真实网络请求
 */
const mock = require('./mock')

const DEFAULT_BASE_URL = 'http://localhost:8088/api'
const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

let baseUrl = DEFAULT_BASE_URL

function getBaseUrl() {
  return baseUrl
}

function setBaseUrl(url) {
  if (url) baseUrl = String(url).replace(/\/+$/, '')
}

function isMock() {
  return !!mock.USE_MOCK
}

function showToast(message) {
  wx.showToast({ title: message || '请求失败', icon: 'none', duration: 2000 })
}

function makeError(code, message) {
  const err = new Error(message || '请求失败')
  err.code = code
  return err
}

function buildQuery(path, params) {
  if (!params) return path
  const keys = Object.keys(params)
  const pairs = []
  keys.forEach(function (key) {
    const value = params[key]
    if (value === undefined || value === null || value === '') return
    pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(value))
  })
  if (!pairs.length) return path
  return path + (path.indexOf('?') >= 0 ? '&' : '?') + pairs.join('&')
}

function goLogin() {
  const pages = getCurrentPages()
  const current = pages.length ? pages[pages.length - 1] : null
  if (current && current.route === 'pages/login/index') return
  wx.navigateTo({ url: '/pages/login/index' })
}

/**
 * 登录态失效：清缓存 + 提示 + 跳登录
 * silent 模式（如启动时静默刷新用户）只清缓存，不打扰用户
 */
function handleUnauthorized(options) {
  try {
    wx.removeStorageSync(TOKEN_KEY)
    wx.removeStorageSync(USER_KEY)
  } catch (e) {
    // ignore
  }
  if (options && options.silent) return
  showToast('登录已失效，请重新登录')
  setTimeout(goLogin, 200)
}

/**
 * 拆响应体
 */
function handleBody(body, options, resolve, reject) {
  if (!body || typeof body !== 'object') {
    if (!options.silent) showToast('服务器返回格式异常')
    reject(makeError(-1, '服务器返回格式异常'))
    return
  }
  const code = Number(body.code)
  if (code === 0) {
    resolve(body.data)
    return
  }
  if (code === 401 || code === 40100 || code === 40101) {
    handleUnauthorized(options)
    reject(makeError(code, body.message || '登录已失效'))
    return
  }
  if (!options.silent) showToast(body.message || '请求失败')
  reject(makeError(code, body.message || '请求失败'))
}

/**
 * 发起请求
 * @param {String} path 形如 '/v1/listings'
 * @param {Object} options { method, data, loading, loadingText, silent, timeout }
 * @returns {Promise<any>} 已拆包的 data
 */
function request(path, options) {
  const opts = Object.assign(
    {
      method: 'GET',
      data: {},
      loading: false,
      loadingText: '加载中',
      silent: false,
      timeout: 15000
    },
    options || {}
  )
  const method = String(opts.method || 'GET').toUpperCase()
  const hasBody = method !== 'GET'
  const url = hasBody ? path : buildQuery(path, opts.data)

  let task = new Promise(function (resolve, reject) {
    const token = wx.getStorageSync(TOKEN_KEY)
    const header = { 'content-type': 'application/json' }
    if (token) header.Authorization = 'Bearer ' + token

    if (mock.USE_MOCK) {
      setTimeout(function () {
        mock
          .dispatch({ url: url, method: method, data: hasBody ? opts.data : {}, token: token })
          .then(function (body) {
            handleBody(body, opts, resolve, reject)
          })
          .catch(function (err) {
            if (!opts.silent) showToast(err && err.message ? err.message : '本地数据异常')
            reject(err)
          })
      }, mock.LATENCY || 300)
      return
    }

    wx.request({
      url: baseUrl + url,
      method: method,
      data: hasBody ? opts.data : undefined,
      header: header,
      timeout: opts.timeout,
      success: function (res) {
        if (res.statusCode === 401) {
          handleUnauthorized(opts)
          reject(makeError(401, '登录已失效'))
          return
        }
        if (res.statusCode >= 500) {
          if (!opts.silent) showToast('服务暂时不可用，请稍后再试')
          reject(makeError(res.statusCode, '服务暂时不可用'))
          return
        }
        handleBody(res.data, opts, resolve, reject)
      },
      fail: function (err) {
        if (!opts.silent) showToast('网络连接失败，请检查后端是否已启动')
        reject(makeError(-2, (err && err.errMsg) || '网络连接失败'))
      }
    })
  })

  if (!opts.loading) return task

  wx.showLoading({ title: opts.loadingText || '加载中', mask: true })
  return task.then(
    function (data) {
      wx.hideLoading()
      return data
    },
    function (err) {
      wx.hideLoading()
      throw err
    }
  )
}

function get(path, data, options) {
  return request(path, Object.assign({ method: 'GET', data: data || {} }, options || {}))
}

function post(path, data, options) {
  return request(path, Object.assign({ method: 'POST', data: data || {} }, options || {}))
}

/**
 * 生成请求幂等号（下单用，重试时保持不变）
 */
function genRequestId() {
  return 'req-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

module.exports = {
  request: request,
  get: get,
  post: post,
  getBaseUrl: getBaseUrl,
  setBaseUrl: setBaseUrl,
  isMock: isMock,
  genRequestId: genRequestId,
  showToast: showToast,
  TOKEN_KEY: TOKEN_KEY
}
