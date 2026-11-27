/**
 * 卖书页（tabBar 页面）
 * 流程：扫 ISBN / 搜教材 → 选品相 → 填价格（带同款价格参考）→ 提交上架
 * 接口：GET /v1/textbooks?keyword=&isbn=、GET /v1/textbooks/{id}/price-index、POST /v1/listings
 */
const { request } = require('../../utils/request')
const fmt = require('../../utils/format')
const auth = require('../../utils/auth')

Page({
  data: {
    keyword: '',
    textbooks: [],
    searching: false,
    searched: false,
    selected: null,
    courseName: '',
    conditionList: fmt.CONDITION_LIST,
    conditionLevel: 'LIKE_NEW',
    conditionText: '9成新',
    price: '',
    priceIndex: null,
    priceRange: '',
    suggestPrice: '',
    description: '',
    dorm: '',
    submitting: false,
    canSubmit: false
  },

  onLoad() {
    const user = getApp().globalData.userInfo
    if (user) {
      this.setData({ dorm: user.dorm || '' })
    }
  },

  onShow() {
    const user = getApp().globalData.userInfo
    if (user && !this.data.dorm) {
      this.setData({ dorm: user.dorm || '' })
    }
  },

  /* ---------------- 选教材 ---------------- */

  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    const that = this
    const keyword = String(this.data.keyword || '').trim()
    if (!keyword) {
      wx.showToast({ title: '请输入书名或 ISBN', icon: 'none' })
      return
    }
    this.setData({ searching: true })
    request('/v1/textbooks', { data: { keyword: keyword } })
      .then(function (list) {
        that.setData({
          textbooks: list || [],
          searching: false,
          searched: true
        })
      })
      .catch(function () {
        that.setData({ searching: false, searched: true })
      })
  },

  onScan() {
    const that = this
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['barCode', 'qrCode'],
      success: function (res) {
        const isbn = String(res.result || '').replace(/[^0-9Xx]/g, '')
        if (!isbn) {
          wx.showToast({ title: '未识别到 ISBN', icon: 'none' })
          return
        }
        that.searchByIsbn(isbn)
      },
      fail: function () {
        // 用户取消扫码
      }
    })
  },

  searchByIsbn(isbn) {
    const that = this
    wx.showLoading({ title: '识别中', mask: true })
    request('/v1/textbooks', { data: { isbn: isbn }, silent: true })
      .then(function (list) {
        wx.hideLoading()
        if (!list || !list.length) {
          that.setData({ keyword: isbn, textbooks: [], searched: true })
          wx.showToast({ title: '教材库里还没有这个 ISBN，可手动搜索', icon: 'none' })
          return
        }
        that.selectTextbook(list[0])
      })
      .catch(function () {
        wx.hideLoading()
        wx.showToast({ title: '识别失败，请重试', icon: 'none' })
      })
  },

  onSelectBook(e) {
    const index = Number(e.currentTarget.dataset.index)
    const book = this.data.textbooks[index]
    if (book) this.selectTextbook(book)
  },

  selectTextbook(book) {
    this.setData({
      selected: book,
      textbooks: [],
      searched: false,
      keyword: '',
      courseName: book.courseName || '',
      description: ''
    })
    this.loadPriceIndex(book.id)
  },

  changeTextbook() {
    this.setData({ selected: null, priceIndex: null, priceRange: '', suggestPrice: '' })
  },

  loadPriceIndex(textbookId) {
    const that = this
    return request('/v1/textbooks/' + textbookId + '/price-index', { silent: true })
      .then(function (index) {
        if (!index || !index.count) {
          const base = that.data.selected ? Number(that.data.selected.price) * 0.4 : 0
          that.setData({
            priceIndex: index || null,
            priceRange: '',
            suggestPrice: base ? String(Math.round(base * 2) / 2) : ''
          })
          return
        }
        that.setData({
          priceIndex: index,
          priceRange:
            '同款在售 ' +
            index.count +
            ' 本 · ¥' +
            fmt.formatPrice(index.min) +
            ' - ¥' +
            fmt.formatPrice(index.max) +
            '，均价 ¥' +
            fmt.formatPrice(index.avg),
          suggestPrice: String(Math.max(1, Math.round(index.avg * 0.9 * 2) / 2))
        })
      })
      .catch(function () {
        that.setData({ priceIndex: null, priceRange: '', suggestPrice: '' })
      })
  },

  useSuggest() {
    if (!this.data.suggestPrice) return
    this.setData({ price: this.data.suggestPrice })
    this.checkCanSubmit()
  },

  /* ---------------- 表单 ---------------- */

  onCondition(e) {
    const value = e.currentTarget.dataset.value
    this.setData({
      conditionLevel: value,
      conditionText: fmt.conditionText(value)
    })
  },

  onPrice(e) {
    this.setData({ price: e.detail.value })
    this.checkCanSubmit()
  },

  onDesc(e) {
    this.setData({ description: e.detail.value })
  },

  onDorm(e) {
    this.setData({ dorm: e.detail.value })
  },

  checkCanSubmit() {
    const price = Number(this.data.price)
    const ok = !!this.data.selected && price > 0
    this.setData({ canSubmit: ok })
  },

  onSubmit() {
    const that = this
    const book = this.data.selected
    const price = Number(this.data.price)

    if (!auth.requireLogin()) return
    if (!book) {
      wx.showToast({ title: '请先选择教材', icon: 'none' })
      return
    }
    if (!price || price <= 0) {
      wx.showToast({ title: '请填写正确的售价', icon: 'none' })
      return
    }
    if (price > Number(book.price)) {
      wx.showToast({ title: '售价不应高于定价 ¥' + fmt.formatPrice(book.price), icon: 'none' })
      return
    }
    if (this.data.submitting) return

    this.setData({ submitting: true })
    request('/v1/listings', {
      method: 'POST',
      loading: true,
      loadingText: '上架中',
      data: {
        textbookId: book.id,
        courseId: book.courseId || 0,
        price: price,
        conditionLevel: this.data.conditionLevel,
        description: String(this.data.description || '').trim(),
        dorm: String(this.data.dorm || '').trim(),
        images: []
      }
    })
      .then(function () {
        that.setData({ submitting: false })
        wx.showToast({ title: '上架成功', icon: 'success' })
        that.resetForm()
        setTimeout(function () {
          wx.navigateTo({ url: '/pages/mine/listings' })
        }, 600)
      })
      .catch(function () {
        that.setData({ submitting: false })
      })
  },

  resetForm() {
    this.setData({
      keyword: '',
      textbooks: [],
      searched: false,
      selected: null,
      price: '',
      priceIndex: null,
      priceRange: '',
      suggestPrice: '',
      description: '',
      conditionLevel: 'LIKE_NEW',
      conditionText: '9成新',
      canSubmit: false
    })
  },

  goMyListings() {
    wx.navigateTo({ url: '/pages/mine/listings' })
  }
})
