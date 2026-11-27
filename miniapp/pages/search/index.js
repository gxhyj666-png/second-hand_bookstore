/**
 * 搜索页
 * 关键词 + 筛选（课程 / 品相 / 排序）+ 结果列表
 * 接口：GET /v1/listings?keyword=&courseId=&conditionLevel=&sort=&page=&size=
 */
const { request } = require('../../utils/request')
const fmt = require('../../utils/format')

const HISTORY_KEY = 'searchHistory'
const PAGE_SIZE = 10

Page({
  data: {
    keyword: '',
    isbn: '',
    courseId: 0,
    courseName: '',
    courseIndex: 0,
    courseNames: ['全部课程'],
    courses: [],
    condition: '',
    conditionLabel: '全部品相',
    conditions: [{ value: '', label: '全部品相' }].concat(
      fmt.CONDITION_LIST.map(function (item) {
        return { value: item.value, label: item.label }
      })
    ),
    sortIndex: 0,
    sorts: fmt.SORT_LIST,
    panelOpen: false,
    history: [],
    listings: [],
    total: 0,
    page: 1,
    size: PAGE_SIZE,
    hasMore: false,
    loading: false,
    searched: false,
    filterCount: 0
  },

  onLoad(options) {
    const keyword = options.keyword ? decodeURIComponent(options.keyword) : ''
    const isbn = options.isbn ? decodeURIComponent(options.isbn) : ''
    const courseId = options.courseId ? Number(options.courseId) : 0
    const courseName = options.courseName ? decodeURIComponent(options.courseName) : ''

    this.setData({
      keyword: isbn || keyword,
      isbn: isbn,
      courseId: courseId,
      courseName: courseName,
      history: this.readHistory()
    })
    this.loadCourses()

    if (isbn || keyword || courseId) {
      this.doSearch(true)
    }
  },

  onShow() {
    this.setData({ history: this.readHistory() })
    // 首页课程卡、扫 ISBN、接龙跳搜索都用 switchTab（tab 页不能带参数），参数经 storage 传递
    const pending = wx.getStorageSync('searchPending')
    if (pending && Object.keys(pending).length) {
      wx.removeStorageSync('searchPending')
      const keyword = pending.isbn || pending.keyword || ''
      this.setData({
        keyword: keyword,
        isbn: pending.isbn || '',
        courseId: pending.courseId || 0,
        courseName: pending.courseName || ''
      })
      this.doSearch(true)
    }
  },

  onReachBottom() {
    if (!this.data.hasMore || this.data.loading) return
    this.doSearch(false)
  },

  /* ---------------- 数据 ---------------- */

  loadCourses() {
    const that = this
    request('/v1/courses', { silent: true })
      .then(function (list) {
        const courses = list || []
        that.setData({
          courses: courses,
          courseNames: ['全部课程'].concat(
            courses.map(function (item) {
              return item.name
            })
          )
        })
        if (that.data.courseId) {
          const index = courses.findIndex
            ? courses.findIndex(function (item) {
                return item.id === that.data.courseId
              })
            : -1
          if (index >= 0) {
            that.setData({ courseIndex: index + 1, courseName: courses[index].name })
          }
        }
      })
      .catch(function () {})
  },

  doSearch(reset) {
    const that = this
    const isReset = reset !== false
    const page = isReset ? 1 : this.data.page + 1
    const sort = this.data.sorts[this.data.sortIndex] || this.data.sorts[0]

    this.setData({ loading: true })
    return request('/v1/listings', {
      data: {
        keyword: this.data.keyword,
        courseId: this.data.courseId || '',
        conditionLevel: this.data.condition || '',
        sort: sort.value,
        page: page,
        size: this.data.size
      }
    })
      .then(function (res) {
        const records = (res && res.records) || []
        const total = (res && res.total) || records.length
        const list = isReset ? records : that.data.listings.concat(records)
        that.setData({
          listings: list,
          total: total,
          page: page,
          hasMore: list.length < total,
          loading: false,
          searched: true,
          filterCount: that.countFilters()
        })
      })
      .catch(function () {
        that.setData({ loading: false, searched: true })
      })
  },

  countFilters() {
    let count = 0
    if (this.data.courseId) count += 1
    if (this.data.condition) count += 1
    if (this.data.sortIndex) count += 1
    return count
  },

  /* ---------------- 搜索历史 ---------------- */

  readHistory() {
    try {
      const list = wx.getStorageSync(HISTORY_KEY)
      return Array.isArray(list) ? list : []
    } catch (e) {
      return []
    }
  },

  saveHistory(keyword) {
    if (!keyword) return
    const list = this.readHistory().filter(function (item) {
      return item !== keyword
    })
    list.unshift(keyword)
    try {
      wx.setStorageSync(HISTORY_KEY, list.slice(0, 10))
    } catch (e) {
      // ignore
    }
    this.setData({ history: list.slice(0, 10) })
  },

  clearHistory() {
    try {
      wx.removeStorageSync(HISTORY_KEY)
    } catch (e) {
      // ignore
    }
    this.setData({ history: [] })
  },

  /* ---------------- 交互 ---------------- */

  onInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    const keyword = String(this.data.keyword || '').trim()
    this.setData({ keyword: keyword, isbn: '', panelOpen: false })
    if (keyword) this.saveHistory(keyword)
    this.doSearch(true)
  },

  onClear() {
    this.setData({ keyword: '', isbn: '', listings: [], total: 0, searched: false })
  },

  onHistoryTap(e) {
    const keyword = e.currentTarget.dataset.value
    this.setData({ keyword: keyword, isbn: '' })
    this.saveHistory(keyword)
    this.doSearch(true)
  },

  togglePanel() {
    this.setData({ panelOpen: !this.data.panelOpen })
  },

  closePanel() {
    this.setData({ panelOpen: false })
  },

  onCourseChange(e) {
    const index = Number(e.detail.value)
    const course = this.data.courses[index - 1]
    this.setData({
      courseIndex: index,
      courseId: course ? course.id : 0,
      courseName: course ? course.name : ''
    })
    this.doSearch(true)
  },

  onConditionTap(e) {
    const value = e.currentTarget.dataset.value
    const label = e.currentTarget.dataset.label
    this.setData({ condition: value, conditionLabel: label })
    this.doSearch(true)
  },

  onSortTap(e) {
    const index = Number(e.currentTarget.dataset.index)
    this.setData({ sortIndex: index })
    this.doSearch(true)
  },

  resetFilters() {
    this.setData({
      courseId: 0,
      courseName: '',
      courseIndex: 0,
      condition: '',
      conditionLabel: '全部品相',
      sortIndex: 0,
      panelOpen: false
    })
    this.doSearch(true)
  },

  goPublish() {
    wx.switchTab({ url: '/pages/listing/publish' })
  }
})
