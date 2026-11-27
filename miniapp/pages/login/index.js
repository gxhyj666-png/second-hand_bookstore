/**
 * 登录 / 注册
 * POST /v1/auth/login    { account, password }
 * POST /v1/auth/register { phone, password, nickname }
 */
const auth = require('../../utils/auth')

const DEMO_ACCOUNTS = [
  { account: 'buyer001', label: '买家 · 小黄同学' },
  { account: 'seller001', label: '卖家 · 学长清仓' },
  { account: 'clerk001', label: '店员 · 三食堂店员' }
]

Page({
  data: {
    mode: 'login',
    account: '',
    phone: '',
    nickname: '',
    password: '',
    showPassword: false,
    submitting: false,
    demos: DEMO_ACCOUNTS,
    agree: true
  },

  onLoad(options) {
    if (auth.isLoggedIn()) {
      this.backOrHome()
    }
  },

  /* ---------------- 输入 ---------------- */

  onMode(e) {
    const mode = e.currentTarget.dataset.mode
    if (mode === this.data.mode) return
    this.setData({ mode: mode, password: '' })
  },

  onField(e) {
    const field = e.currentTarget.dataset.field
    const patch = {}
    patch[field] = e.detail.value
    this.setData(patch)
  },

  togglePassword() {
    this.setData({ showPassword: !this.data.showPassword })
  },

  onAgree() {
    this.setData({ agree: !this.data.agree })
  },

  fillDemo(e) {
    const account = e.currentTarget.dataset.account
    this.setData({
      mode: 'login',
      account: account,
      password: '123456',
      agree: true
    })
  },

  /* ---------------- 提交 ---------------- */

  onSubmit() {
    const that = this
    if (this.data.submitting) return
    if (!this.data.agree) {
      wx.showToast({ title: '请先同意用户协议', icon: 'none' })
      return
    }

    if (this.data.mode === 'login') {
      const account = String(this.data.account || '').trim()
      const password = String(this.data.password || '')
      if (!account) {
        wx.showToast({ title: '请输入手机号或账号', icon: 'none' })
        return
      }
      if (!password) {
        wx.showToast({ title: '请输入密码', icon: 'none' })
        return
      }
      this.setData({ submitting: true })
      auth
        .login(account, password)
        .then(function (data) {
          that.setData({ submitting: false })
          that.afterLogin(data && data.user)
        })
        .catch(function () {
          that.setData({ submitting: false })
        })
      return
    }

    const phone = String(this.data.phone || '').trim()
    const password = String(this.data.password || '')
    const nickname = String(this.data.nickname || '').trim()
    if (!/^1\d{10}$/.test(phone)) {
      wx.showToast({ title: '请输入 11 位手机号', icon: 'none' })
      return
    }
    if (password.length < 6) {
      wx.showToast({ title: '密码至少 6 位', icon: 'none' })
      return
    }
    if (!nickname) {
      wx.showToast({ title: '起个昵称，方便同学认出你', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    auth
      .register({ phone: phone, password: password, nickname: nickname })
      .then(function (data) {
        that.setData({ submitting: false })
        that.afterLogin(data && data.user)
      })
      .catch(function () {
        that.setData({ submitting: false })
      })
  },

  afterLogin(user) {
    const app = getApp()
    if (app && app.globalData && user) app.globalData.userInfo = user
    wx.showToast({ title: '欢迎回来', icon: 'success' })
    setTimeout(this.backOrHome.bind(this), 600)
  },

  backOrHome() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/index/index' })
    }
  },

  onAgreement() {
    wx.showModal({
      title: '用户协议与隐私说明',
      content:
        '同窗书仅用于校内二手教材流转。交易通过自提点核销担保，钱款在核销后结算给卖家。请如实描述教材品相，虚假描述会扣减信用分。',
      showCancel: false,
      confirmText: '知道了'
    })
  }
})
