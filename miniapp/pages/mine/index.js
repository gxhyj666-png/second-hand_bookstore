/**
 * 我的
 * 数据：GET /v1/auth/me、GET /v1/listings/mine、GET /v1/messages/unread-count、GET /v1/messages、GET /v1/group-buys/{code}
 * 收藏列表用本地 storage 快照（收藏接口只返回状态，没有列表接口）
 */
const { request } = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const app = getApp()

Page({
  data: {
    logged: false,
    user: null,
    unread: 0,
    onSaleCount: 0,
    soldCount: 0,
    favoriteCount: 0,
    favorites: [],
    group: null,
    messages: [],
    msgOpen: false,
    loading: false
  },

  onShow() {
    this.syncUser()
    if (auth.isLoggedIn()) {
      this.loadAll()
    } else {
      this.setData({
        user: null,
        logged: false,
        unread: 0,
        onSaleCount: 0,
        soldCount: 0,
        favorites: [],
        favoriteCount: 0
      })
    }
  },

  onPullDownRefresh() {
    const that = this
    this.syncUser()
    if (!auth.isLoggedIn()) {
      wx.stopPullDownRefresh()
      return
    }
    this.loadAll().then(function () {
      wx.stopPullDownRefresh()
    })
  },

  syncUser() {
    const user = auth.getUser() || (app && app.globalData ? app.globalData.userInfo : null)
    const list = wx.getStorageSync('favorites')
    const favorites = (Array.isArray(list) ? list : []).map(function (item) {
      return {
        id: item.id,
        title: item.title,
        edition: item.edition || '',
        dorm: item.dorm || '',
        priceText: fmt.formatPrice(item.price),
        conditionText: fmt.conditionText(item.conditionLevel)
      }
    })
    this.setData({
      user: user,
      logged: !!user,
      avatarText: user && user.nickname ? String(user.nickname).slice(0, 1) : '同学',
      favorites: favorites,
      favoriteCount: favorites.length
    })
  },

  loadAll() {
    const that = this
    return Promise.all([
      request('/v1/messages/unread-count', { silent: true }).catch(function () {
        return { count: 0 }
      }),
      request('/v1/listings/mine?status=ON_SALE', { silent: true }).catch(function () {
        return []
      }),
      request('/v1/listings/mine?status=SOLD', { silent: true }).catch(function () {
        return []
      }),
      request('/v1/group-buys/MATH7A', { silent: true }).catch(function () {
        return null
      })
    ]).then(function (res) {
      that.setData({
        unread: (res[0] && res[0].count) || 0,
        onSaleCount: (res[1] || []).length,
        soldCount: (res[2] || []).length,
        group: res[3] || null
      })
    })
  },

  /* ---------------- 跳转 ---------------- */

  onLogin() {
    wx.navigateTo({ url: '/pages/login/index' })
  },

  goListings() {
    if (!auth.requireLogin()) return
    wx.navigateTo({ url: '/pages/mine/listings' })
  },

  goGroup() {
    wx.navigateTo({ url: '/pages/groupbuy/detail?code=MATH7A' })
  },

  goVerify() {
    if (!auth.requireLogin()) return
    wx.navigateTo({ url: '/pages/verify/index' })
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/listing/detail?id=' + e.currentTarget.dataset.id })
  },

  goPublish() {
    wx.switchTab({ url: '/pages/listing/publish' })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  /* ---------------- 消息 ---------------- */

  openMessages() {
    const that = this
    if (!auth.requireLogin()) return
    request('/v1/messages').then(function (list) {
      that.setData({ messages: list || [], msgOpen: true })
    })
  },

  closeMessages() {
    this.setData({ msgOpen: false })
  },

  /* ---------------- 设置 ---------------- */

  onCreditInfo() {
    wx.showModal({
      title: '信用分说明',
      content:
        '按时到店核销 +2 分，评价一次 +1 分；爽约未核销 -5 分，虚假描述经核实 -10 分。\n信用分低于 60 分将限制上架，刷到 100 分以上可以在平台优先展示。',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  onClearCache() {
    const that = this
    wx.showModal({
      title: '清除本地缓存',
      content: '将清空搜索历史、收藏快照和本地登录状态，需要重新登录。',
      confirmText: '清除',
      confirmColor: '#e2453c',
      success: function (res) {
        if (!res.confirm) return
        wx.clearStorageSync()
        if (app && app.globalData) app.globalData.userInfo = null
        that.syncUser()
        that.setData({ unread: 0, onSaleCount: 0, soldCount: 0, group: null })
        wx.showToast({ title: '已清除', icon: 'success' })
      }
    })
  },

  onAbout() {
    wx.showModal({
      title: '关于同窗书',
      content:
        '同窗书是校园教材循环平台：按课表找书、版本校验、到店核销担保交易，让一本书在一届届同学之间继续流转。\n\n版本 1.0.0 · 毕业设计演示',
      showCancel: false,
      confirmText: '好的'
    })
  },

  onLogout() {
    const that = this
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新登录才能下单和卖书。',
      confirmText: '退出',
      confirmColor: '#e2453c',
      success: function (res) {
        if (!res.confirm) return
        auth.logout()
        if (app && app.globalData) app.globalData.userInfo = null
        that.setData({
          user: null,
          logged: false,
          unread: 0,
          onSaleCount: 0,
          soldCount: 0,
          group: null
        })
        wx.showToast({ title: '已退出', icon: 'success' })
      }
    })
  }
})
