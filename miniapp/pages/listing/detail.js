/**
 * 教材详情页
 * 接口：GET /v1/listings/{id}、GET /v1/pickup-points?schoolId=1、POST /v1/listings/{id}/favorite
 */
const { request } = require('../../utils/request')
const fmt = require('../../utils/format')
const auth = require('../../utils/auth')

Page({
  data: {
    id: 0,
    detail: null,
    loading: true,
    tone: 0,
    coverTitle: '',
    conditionText: '',
    versionBadge: '教材',
    priceText: '0.00',
    originalPriceText: '',
    distanceText: '',
    savePercent: 0,
    favorite: false,
    points: [],
    canBuy: false,
    isOwner: false
  },

  onLoad(options) {
    const id = Number(options.id || 0)
    if (!id) {
      wx.showToast({ title: '缺少教材编号', icon: 'none' })
      return
    }
    this.setData({ id: id })
    this.loadDetail()
    this.loadPoints()
  },

  onPullDownRefresh() {
    const that = this
    this.loadDetail().then(function () {
      wx.stopPullDownRefresh()
    })
  },

  loadDetail() {
    const that = this
    this.setData({ loading: true })
    return request('/v1/listings/' + this.data.id, { loading: false })
      .then(function (res) {
        const detail = res || {}
        const edition = detail.edition || ''
        let badge = '校内教材'
        if (detail.schoolRequired) {
          badge = '本校指定教材' + (edition ? ' · ' + edition : '')
        } else if (detail.bindType === 'REFERENCE') {
          badge = '课程参考书' + (edition ? ' · ' + edition : '')
        } else if (edition) {
          badge = edition
        }

        const title = detail.title || ''
        const user = getApp().globalData.userInfo
        that.setData({
          detail: detail,
          loading: false,
          tone: Number(detail.id || 0) % 5,
          coverTitle: title,
          conditionText: detail.conditionText || fmt.conditionText(detail.conditionLevel),
          versionBadge: badge,
          priceText: fmt.formatPrice(detail.price),
          originalPriceText: detail.originalPrice ? fmt.formatPrice(detail.originalPrice) : '',
          distanceText: detail.distanceText || fmt.distanceText(detail.distanceKm),
          savePercent: fmt.discountPercent(detail.price, detail.originalPrice),
          favorite: !!detail.favorite,
          canBuy: detail.status === 'ON_SALE',
          isOwner: !!(user && detail.seller && detail.seller.id === user.id)
        })
      })
      .catch(function () {
        that.setData({ loading: false })
      })
  },

  loadPoints() {
    const that = this
    request('/v1/pickup-points', { data: { schoolId: 1 }, silent: true })
      .then(function (list) {
        const points = (list || []).map(function (point) {
          return Object.assign({}, point, {
            distanceText: point.distanceText || fmt.distanceText(point.distance)
          })
        })
        that.setData({ points: points })
      })
      .catch(function () {})
  },

  previewCover() {
    wx.previewImage({
      current: '',
      urls: (this.data.detail && this.data.detail.images) || [],
      fail: function () {}
    })
  },

  onFavorite() {
    const that = this
    if (!auth.requireLogin()) return
    request('/v1/listings/' + this.data.id + '/favorite', { method: 'POST', data: {} })
      .then(function (res) {
        that.setData({ favorite: !!res.favorited })
        wx.showToast({
          title: res.favorited ? '已加入收藏' : '已取消收藏',
          icon: 'none'
        })
        if (res.favorited && that.data.detail) {
          const detail = that.data.detail
          const snapshot = {
            id: detail.id,
            title: detail.title,
            price: detail.price,
            conditionLevel: detail.conditionLevel,
            edition: detail.edition || '',
            dorm: detail.dorm || '',
            coverUrl: detail.coverUrl || ''
          }
          try {
            const raw = wx.getStorageSync('favorites')
            const list = Array.isArray(raw) ? raw : []
            const next = list.filter(function (item) {
              return item.id !== snapshot.id
            })
            next.unshift(snapshot)
            wx.setStorageSync('favorites', next.slice(0, 30))
          } catch (e) {
            // ignore
          }
        }
      })
      .catch(function () {})
  },

  onContact() {
    const detail = this.data.detail
    if (!detail) return
    wx.showModal({
      title: '联系卖家',
      content:
        '卖家：' +
        ((detail.seller && detail.seller.nickname) || '同校同学') +
        '\n宿舍：' +
        (detail.dorm || '未填写') +
        '\n\n平台建议在自提点当面交易，确认无误后再核销。',
      showCancel: false,
      confirmText: '知道了'
    })
  },

  onOrder() {
    const detail = this.data.detail
    if (!detail) return
    if (!this.data.canBuy) {
      wx.showToast({ title: '该教材已下架或售出', icon: 'none' })
      return
    }
    if (this.data.isOwner) {
      wx.showToast({ title: '不能购买自己上架的教材', icon: 'none' })
      return
    }
    if (!auth.requireLogin()) return
    wx.navigateTo({ url: '/pages/order/confirm?listingId=' + detail.id })
  }
})
