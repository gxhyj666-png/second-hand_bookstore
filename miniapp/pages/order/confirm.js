/**
 * 确认订单页
 * 流程：选教材自提点 → POST /v1/orders（带 requestId 幂等）→ POST /v1/orders/{orderNo}/pay（模拟支付）
 * 接口：GET /v1/listings/{id}、GET /v1/pickup-points、POST /v1/orders、POST /v1/orders/{orderNo}/pay
 */
const { request, genRequestId } = require('../../utils/request')
const fmt = require('../../utils/format')
const auth = require('../../utils/auth')

Page({
  data: {
    listingId: 0,
    listing: null,
    points: [],
    pointId: 0,
    amountText: '0.00',
    feeText: '0.00',
    totalText: '0.00',
    submitting: false,
    payChannel: 'MOCK'
  },

  onLoad(options) {
    const listingId = Number(options.listingId || 0)
    if (!listingId) {
      wx.showToast({ title: '缺少教材编号', icon: 'none' })
      return
    }
    // 幂等号：同一笔下单重试复用，避免重复建单
    this.requestId = genRequestId()
    this.setData({ listingId: listingId })
    this.loadListing()
    this.loadPoints()
  },

  loadListing() {
    const that = this
    request('/v1/listings/' + this.data.listingId)
      .then(function (res) {
        const amount = Number((res && res.price) || 0)
        that.setData({
          listing: res,
          amountText: fmt.formatPrice(amount),
          totalText: fmt.formatPrice(amount)
        })
      })
      .catch(function () {})
  },

  loadPoints() {
    const that = this
    request('/v1/pickup-points', { data: { schoolId: 1 } })
      .then(function (list) {
        const points = (list || []).map(function (point) {
          return Object.assign({}, point, {
            distanceText: point.distanceText || fmt.distanceText(point.distance)
          })
        })
        that.setData({
          points: points,
          pointId: points.length ? points[0].id : 0
        })
      })
      .catch(function () {})
  },

  onPickPoint(e) {
    this.setData({ pointId: Number(e.currentTarget.dataset.id) })
  },

  onSubmit() {
    const that = this
    if (!auth.requireLogin()) return
    if (!this.data.listing) {
      wx.showToast({ title: '教材信息加载中', icon: 'none' })
      return
    }
    if (!this.data.pointId) {
      wx.showToast({ title: '请选择自提点', icon: 'none' })
      return
    }
    if (this.data.submitting) return

    this.setData({ submitting: true })
    request('/v1/orders', {
      method: 'POST',
      loading: true,
      loadingText: '提交订单',
      data: {
        listingId: this.data.listingId,
        pointId: this.data.pointId,
        requestId: this.requestId
      }
    })
      .then(function (res) {
        that.setData({ submitting: false })
        that.orderNo = res && res.orderNo
        that.expireAt = res && res.expireAt
        that.askPay(res && res.amount)
      })
      .catch(function () {
        that.setData({ submitting: false })
      })
  },

  askPay(amount) {
    const that = this
    const text = fmt.formatPrice(amount || this.data.listing.price)
    wx.showModal({
      title: '模拟支付',
      content: '订单已创建，需支付 ¥' + text + '。\n演示环境不会产生真实扣款，支付后资金进入平台冻结。',
      confirmText: '确认支付',
      confirmColor: '#2f7d32',
      success: function (res) {
        if (res.confirm) that.doPay()
      }
    })
  },

  doPay() {
    const that = this
    if (!this.orderNo) return
    request('/v1/orders/' + this.orderNo + '/pay', {
      method: 'POST',
      loading: true,
      loadingText: '支付中',
      data: { channel: 'MOCK' }
    })
      .then(function () {
        wx.showToast({ title: '支付成功', icon: 'success' })
        setTimeout(function () {
          wx.redirectTo({ url: '/pages/order/detail?orderNo=' + that.orderNo })
        }, 500)
      })
      .catch(function () {})
  },

  goBack() {
    wx.navigateBack({ delta: 1 })
  }
})
