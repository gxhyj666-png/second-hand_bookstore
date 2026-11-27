/**
 * 首页
 * 结构：学校信息 + 搜索入口 + 我的课程（按课表挂教材）+ 同校在售推荐
 * 接口：GET /v1/courses、GET /v1/courses/{id}/textbooks、GET /v1/listings、GET /v1/messages/unread-count
 */
const { request } = require('../../utils/request')
const app = getApp()

const PAGE_SIZE = 8

Page({
  data: {
    schoolName: '示例大学',
    term: '2026-2027-1',
    unread: 0,
    courses: [],
    listings: [],
    total: 0,
    page: 1,
    hasMore: false,
    loading: true,
    loadingMore: false,
    isMock: false
  },

  onLoad() {
    const global = app.globalData
    this.setData({
      schoolName: global.schoolName || '示例大学',
      isMock: !!global.useMock
    })
    this.loadCourses()
    this.loadListings(false)
    this.loadUnread()
  },

  onShow() {
    this.loadUnread()
  },

  onPullDownRefresh() {
    Promise.all([this.loadCourses(), this.loadListings(false)]).then(function () {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loadingMore) return
    this.loadListings(true)
  },

  /* ---------------- 数据 ---------------- */

  loadCourses() {
    const that = this
    return request('/v1/courses', { silent: true })
      .then(function (list) {
        const courses = (list || []).map(function (course) {
          return Object.assign({}, course, {
            textbookTitle: course.requiredTextbookTitle || '',
            edition: ''
          })
        })
        that.setData({ courses: courses })
        // 只有前 4 门课去补「指定教材」，避免一次打太多请求
        return Promise.all(
          courses.slice(0, 4).map(function (course) {
            return that.fillRequiredTextbook(course)
          })
        )
      })
      .catch(function () {
        that.setData({ courses: [] })
      })
  },

  fillRequiredTextbook(course) {
    const that = this
    return request('/v1/courses/' + course.id + '/textbooks', { silent: true })
      .then(function (books) {
        const required = (books || []).filter(function (book) {
          return book.bindType === 'REQUIRED'
        })[0]
        if (!required) return null
        const courses = that.data.courses.map(function (item) {
          if (item.id !== course.id) return item
          return Object.assign({}, item, {
            textbookTitle: required.title,
            edition: required.edition || ''
          })
        })
        that.setData({ courses: courses })
        return null
      })
      .catch(function () {
        return null
      })
  },

  loadListings(append) {
    const that = this
    const page = append ? this.data.page + 1 : 1
    if (append) this.setData({ loadingMore: true })
    else this.setData({ loading: true })

    return request('/v1/listings', {
      data: { sort: 'recommend', page: page, size: PAGE_SIZE }
    })
      .then(function (res) {
        const records = (res && res.records) || []
        const total = (res && res.total) || records.length
        const list = append ? that.data.listings.concat(records) : records
        that.setData({
          listings: list,
          total: total,
          page: page,
          hasMore: list.length < total,
          loading: false,
          loadingMore: false
        })
      })
      .catch(function () {
        that.setData({ loading: false, loadingMore: false, hasMore: false })
      })
  },

  loadUnread() {
    const that = this
    request('/v1/messages/unread-count', { silent: true })
      .then(function (res) {
        that.setData({ unread: (res && res.count) || 0 })
      })
      .catch(function () {
        that.setData({ unread: 0 })
      })
  },

  /* ---------------- 交互 ---------------- */

  goSearch() {
    // 搜索是 tabBar 页面，switchTab 不支持带参数，跨页参数走 storage
    wx.switchTab({ url: '/pages/search/index' })
  },

  goPublish() {
    wx.switchTab({ url: '/pages/listing/publish' })
  },

  goVerify() {
    wx.navigateTo({ url: '/pages/verify/index' })
  },

  goGroupBuy() {
    wx.navigateTo({ url: '/pages/groupbuy/detail?inviteCode=MATH7A' })
  },

  goMyListings() {
    wx.navigateTo({ url: '/pages/mine/listings' })
  },

  onCourseTap(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name || ''
    wx.setStorageSync('searchPending', { courseId: id, courseName: name })
    wx.switchTab({ url: '/pages/search/index' })
  },

  onScan() {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['barCode', 'qrCode'],
      success: function (res) {
        const code = String(res.result || '').replace(/[^0-9Xx]/g, '')
        if (!code) {
          wx.showToast({ title: '未识别到 ISBN', icon: 'none' })
          return
        }
        wx.setStorageSync('searchPending', { isbn: code })
        wx.switchTab({ url: '/pages/search/index' })
      },
      fail: function () {
        // 用户取消扫码，静默处理
      }
    })
  },

  goMessages() {
    if (!this.data.unread) {
      wx.showToast({ title: '暂无未读消息', icon: 'none' })
      return
    }
    wx.showToast({ title: '有 ' + this.data.unread + ' 条新消息，可在「我的」查看', icon: 'none' })
  }
})
