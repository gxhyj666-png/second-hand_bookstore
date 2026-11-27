/**
 * 订单详情页
 * 重点：待核销状态下展示 6 位核销码，30 秒倒计时，归零自动向后端刷新新码
 * 接口：GET /v1/orders/{orderNo}、POST /v1/orders/{orderNo}/pay、POST /v1/reviews
 */
const { request } = require('../../utils/request')
const fmt = require('../../utils/format')

const REFRESH_SECONDS = 30

Page({
  data: {
    orderNo: '',
    order: null,
    loading: true,
    codeText: '00:30',
    codeSeconds: REFRESH_SECONDS,
    showCode: false,
    stars: [1, 2, 3, 4, 5],
    rating: 5,
    reviewContent: '',
    anonymous: false,
    reviewOpen: false,
    submittingReview: false,
    paying: false
  },

  onLoad(options) {
    const orderNo = options.orderNo || ''
    if (!orderNo) {
      wx.showToast({ title: '缺少订单号', icon: 'none' })
      return
    }
    this.setData({
      orderNo: orderNo,
      reviewOpen: options.review === '1'
    })
    this.loadOrder(true)
  },

  onUnload() {
    this.clearTimer()
  },

  onHide() {
    this.clearTimer()
  },

  onShow() {
    // 从后台切回来时，核销码可能已经过期，重新拉一次
    if (this.data.order && this.data.showCode) this.refreshCode()
  },

  onPullDownRefresh() {
    const that = this
    this.loadOrder(true).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  /* ---------------- 数据 ---------------- */

  loadOrder(withTimer) {
    const that = this
    return request('/v1/orders/' + this.data.orderNo)
      .then(function (res) {
        const order = res || {}
        const showCode = order.status === 'WAIT_VERIFY' || order.status === 'FROZEN'
        const seconds = Math.min(REFRESH_SECONDS, Number(order.codeSeconds) || REFRESH_SECONDS)
        that.setData({
          order: order,
          loading: false,
          showCode: showCode,
          codeSeconds: seconds,
          codeText: fmt.countdownText(seconds)
        })
        if (showCode && withTimer) that.startTimer()
        else that.clearTimer()
      })
      .catch(function () {
        that.setData({ loading: false })
      })
  },

  refreshCode() {
    const that = this
    return request('/v1/orders/' + this.data.orderNo, { silent: true })
      .then(function (res) {
        const order = res || {}
        const showCode = order.status === 'WAIT_VERIFY' || order.status === 'FROZEN'
        const seconds = Math.min(REFRESH_SECONDS, Number(order.codeSeconds) || REFRESH_SECONDS)
        that.setData({
          order: order,
          showCode: showCode,
          codeSeconds: seconds,
          codeText: fmt.countdownText(seconds)
        })
        if (!showCode) that.clearTimer()
      })
      .catch(function () {})
  },

  /* ---------------- 倒计时 ---------------- */

  startTimer() {
    const that = this
    this.clearTimer()
    this.timer = setInterval(function () {
      const next = that.data.codeSeconds - 1
      if (next <= 0) {
        // 归零：换 30 秒时间片，重新拉取新码
        that.setData({ codeSeconds: 0, codeText: '00:00' })
        that.refreshCode()
        return
      }
      that.setData({
        codeSeconds: next,
        codeText: fmt.countdownText(next)
      })
    }, 1000)
  },

  clearTimer() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  /* ---------------- 操作 ---------------- */

  onPay() {
    const that = this
    if (this.data.paying) return
    wx.showModal({
      title: '模拟支付',
      content: '演示环境不会真实扣款，确认支付 ¥' + this.data.order.amountText + ' 吗？',
      confirmText: '确认支付',
      confirmColor: '#2f7d32',
      success: function (res) {
        if (!res.confirm) return
        that.setData({ paying: true })
        request('/v1/orders/' + that.data.orderNo + '/pay', {
          method: 'POST',
          loading: true,
          loadingText: '支付中',
          data: { channel: 'MOCK' }
        })
          .then(function () {
            that.setData({ paying: false })
            wx.showToast({ title: '支付成功', icon: 'success' })
            that.loadOrder(true)
          })
          .catch(function () {
            that.setData({ paying: false })
          })
      }
    })
  },

  openReview() {
    if (!this.data.order || !this.data.order.canReview) return
    this.setData({ reviewOpen: true })
  },

  onStar(e) {
    this.setData({ rating: Number(e.currentTarget.dataset.value) })
  },

  onReviewInput(e) {
    this.setData({ reviewContent: e.detail.value })
  },

  onAnonymous(e) {
    this.setData({ anonymous: !!e.detail.value })
  },

  submitReview() {
    const that = this
    if (this.data.submittingReview) return
    const content = String(this.data.reviewContent || '').trim()
    if (content.length < 2) {
      wx.showToast({ title: '写两句真实感受吧', icon: 'none' })
      return
    }
    this.setData({ submittingReview: true })
    request('/v1/reviews', {
      method: 'POST',
      loading: true,
      loadingText: '提交中',
      data: {
        orderNo: this.data.orderNo,
        rating: this.data.rating,
        content: content,
        anonymous: this.data.anonymous
      }
    })
      .then(function () {
        that.setData({ submittingReview: false, reviewOpen: false })
        wx.showToast({ title: '评价已提交', icon: 'success' })
        that.loadOrder(false)
      })
      .catch(function () {
        that.setData({ submittingReview: false })
      })
  },

  copyOrderNo() {
    wx.setClipboardData({ data: this.data.orderNo })
  },

  callPoint() {
    wx.showModal({
      title: '自提点',
      content:
        ((this.data.order && this.data.order.point && this.data.order.point.name) || '自提点') +
        '\n营业时间：' +
        ((this.data.order && this.data.order.point && this.data.order.point.openTime) || '') +
        ' - ' +
        ((this.data.order && this.data.order.point && this.data.order.point.closeTime) || '') +
        '\n请向店员出示核销码，30 秒刷新一次。',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  }
})
