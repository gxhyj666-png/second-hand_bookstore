/**
 * 我的书架
 * GET /v1/listings/mine?status=ON_SALE|SOLD|WAIT_VERIFY
 */
const { request } = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const TABS = [
  { key: 'ON_SALE', label: '在售' },
  { key: 'SOLD', label: '已售' },
  { key: 'WAIT_VERIFY', label: '待核销' }
]

Page({
  data: {
    tabs: TABS,
    tabIndex: 0,
    status: 'ON_SALE',
    list: [],
    loading: true,
    emptyText: ''
  },

  onShow() {
    if (!auth.isLoggedIn()) {
      wx.redirectTo({ url: '/pages/login/index' })
      return
    }
    this.loadList()
  },

  onPullDownRefresh() {
    const that = this
    this.loadList().then(function () {
      wx.stopPullDownRefresh()
    })
  },

  onTab(e) {
    const index = Number(e.currentTarget.dataset.index)
    if (index === this.data.tabIndex) return
    this.setData({
      tabIndex: index,
      status: TABS[index].key,
      loading: true,
      list: []
    })
    this.loadList()
  },

  loadList() {
    const that = this
    const status = this.data.status
    return request('/v1/listings/mine?status=' + status)
      .then(function (list) {
        const rows = (list || []).map(function (item) {
          return {
            id: item.id,
            title: item.title,
            edition: item.edition || '',
            dorm: item.dorm || '',
            priceText: fmt.formatPrice(item.price),
            originalText: item.originalPrice ? fmt.formatPrice(item.originalPrice) : '',
            conditionText: item.conditionText || fmt.conditionText(item.conditionLevel),
            statusText: item.statusText || fmt.listingStatusText(item.status),
            status: item.status,
            viewCount: item.viewCount || 0
          }
        })
        that.setData({
          list: rows,
          loading: false,
          emptyText:
            status === 'ON_SALE'
              ? '还没有在售的教材，扫码 ISBN 挂一本试试'
              : status === 'SOLD'
                ? '还没有卖出的教材'
                : '没有等待核销的教材'
        })
      })
      .catch(function () {
        that.setData({ loading: false, list: [] })
      })
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/listing/detail?id=' + e.currentTarget.dataset.id })
  },

  goPublish() {
    wx.switchTab({ url: '/pages/listing/publish' })
  },

  onShare(e) {
    const index = Number(e.currentTarget.dataset.index)
    this.shareItem = this.data.list[index] || null
  },

  onShareAppMessage() {
    const item = this.shareItem
    this.shareItem = null
    if (!item) {
      return { title: '同窗书 · 校园教材循环平台', path: '/pages/index/index' }
    }
    return {
      title: '《' + item.title + '》' + item.conditionText + '，¥' + item.priceText,
      path: '/pages/listing/detail?id=' + item.id
    }
  }
})
