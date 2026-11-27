/**
 * 我的订单（tabBar 页面）
 * tab：全部 / 待核销 / 已完成；可切换「我买到的 / 我卖出的」
 * 接口：GET /v1/orders?role=buyer&status=
 */
const { request } = require('../../utils/request')
const auth = require('../../utils/auth')

const PAGE_SIZE = 10

Page({
  data: {
    role: 'buyer',
    roles: [
      { key: 'buyer', label: '我买到的' },
      { key: 'seller', label: '我卖出的' }
    ],
    tabs: [
      { key: '', label: '全部' },
      { key: 'WAIT_VERIFY', label: '待核销' },
      { key: 'VERIFIED', label: '已完成' }
    ],
    activeIndex: 0,
    orders: [],
    loading: true,
    page: 1,
    size: PAGE_SIZE,
    hasMore: false,
    logged: true
  },

  onShow() {
    this.setData({ logged: auth.isLoggedIn() })
    this.loadOrders(true)
  },

  onPullDownRefresh() {
    this.loadOrders(true).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading) return
    this.loadOrders(false)
  },

  loadOrders(reset) {
    const that = this
    if (!auth.isLoggedIn()) {
      this.setData({ loading: false, orders: [], total: 0 })
      return Promise.resolve()
    }
    const isReset = reset !== false
    const page = isReset ? 1 : this.data.page + 1
    const tab = this.data.tabs[this.data.activeIndex] || this.data.tabs[0]

    this.setData({ loading: true })
    return request('/v1/orders', {
      data: {
        role: this.data.role,
        status: tab.key,
        page: page,
        size: this.data.size
      }
    })
      .then(function (list) {
        const records = list || []
        const merged = isReset ? records : that.data.orders.concat(records)
        that.setData({
          orders: merged,
          page: page,
          hasMore: records.length >= that.data.size,
          loading: false
        })
      })
      .catch(function () {
        that.setData({ loading: false })
      })
  },

  switchRole(e) {
    const role = e.currentTarget.dataset.role
    if (role === this.data.role) return
    this.setData({ role: role, orders: [], activeIndex: 0 })
    this.loadOrders(true)
  },

  switchTab(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (index === this.data.activeIndex) return
    this.setData({ activeIndex: index, orders: [] })
    this.loadOrders(true)
  },

  onOrderTap(e) {
    const orderNo = e.currentTarget.dataset.no
    wx.navigateTo({ url: '/pages/order/detail?orderNo=' + orderNo })
  },

  onPay(e) {
    const that = this
    const orderNo = e.currentTarget.dataset.no
    wx.showModal({
      title: '模拟支付',
      content: '演示环境不会真实扣款，确认支付这笔订单吗？',
      confirmText: '确认支付',
      confirmColor: '#2f7d32',
      success: function (res) {
        if (!res.confirm) return
        request('/v1/orders/' + orderNo + '/pay', {
          method: 'POST',
          loading: true,
          loadingText: '支付中',
          data: { channel: 'MOCK' }
        })
          .then(function () {
            wx.showToast({ title: '支付成功', icon: 'success' })
            that.loadOrders(true)
          })
          .catch(function () {})
      }
    })
  },

  onReview(e) {
    const orderNo = e.currentTarget.dataset.no
    wx.navigateTo({ url: '/pages/order/detail?orderNo=' + orderNo + '&review=1' })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
