/**
 * 班级接龙详情
 * GET  /v1/group-buys/{inviteCode}
 * POST /v1/group-buys/{inviteCode}/join  { quantity, contact }
 */
const { request } = require('../../utils/request')
const auth = require('../../utils/auth')

Page({
  data: {
    code: 'MATH7A',
    group: null,
    loading: true,
    quantity: 1,
    contact: '',
    joining: false
  },

  onLoad(options) {
    const code = options.code || options.inviteCode || 'MATH7A'
    this.setData({ code: code })
    this.loadGroup()
  },

  onPullDownRefresh() {
    const that = this
    this.loadGroup().then(function () {
      wx.stopPullDownRefresh()
    })
  },

  onShareAppMessage() {
    const g = this.data.group || {}
    return {
      title: g.title || '班级教材接龙，一起拼更划算',
      path: '/pages/groupbuy/detail?code=' + this.data.code
    }
  },

  loadGroup() {
    const that = this
    return request('/v1/group-buys/' + this.data.code)
      .then(function (res) {
        const group = res || {}
        that.setData({
          group: group,
          loading: false,
          quantity: group.myQuantity || 1,
          contact: group.joined ? that.data.contact : that.data.contact
        })
      })
      .catch(function () {
        that.setData({ loading: false })
      })
  },

  /* ---------------- 表单 ---------------- */

  onMinus() {
    const next = Math.max(1, Number(this.data.quantity) - 1)
    this.setData({ quantity: next })
  },

  onPlus() {
    const next = Math.min(10, Number(this.data.quantity) + 1)
    this.setData({ quantity: next })
  },

  onQtyInput(e) {
    const value = parseInt(e.detail.value, 10)
    if (!value) return
    this.setData({ quantity: Math.min(10, Math.max(1, value)) })
  },

  onContactInput(e) {
    this.setData({ contact: e.detail.value })
  },

  /* ---------------- 加入 ---------------- */

  onJoin() {
    const that = this
    if (!auth.requireLogin()) return
    if (this.data.joining) return
    const contact = String(this.data.contact || '').trim()
    if (!contact) {
      wx.showToast({ title: '请填微信号或手机号，方便通知领取', icon: 'none' })
      return
    }
    this.setData({ joining: true })
    request('/v1/group-buys/' + this.data.code + '/join', {
      method: 'POST',
      loading: true,
      loadingText: '登记中',
      data: {
        quantity: this.data.quantity,
        contact: contact
      }
    })
      .then(function (res) {
        that.setData({ joining: false, group: res || that.data.group })
        wx.showToast({ title: '登记成功', icon: 'success' })
      })
      .catch(function () {
        that.setData({ joining: false })
      })
  },

  copyCode() {
    wx.setClipboardData({ data: this.data.code })
  },

  goBook() {
    const g = this.data.group || {}
    if (!g.textbookId) return
    wx.setStorageSync('searchPending', { keyword: g.textbookTitle || '' })
    wx.switchTab({ url: '/pages/search/index' })
  }
})
