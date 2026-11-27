/**
 * 店员核销台
 * 流程：买家到自提点 → 出示 30 秒轮换的 6 位核销码 → 店员扫码/输入 → POST /v1/orders/{orderNo}/verify
 * 本页同时统计「今日核销数」，并展示本次会话的核销记录
 */
const { request, isMock } = require('../../utils/request')
const auth = require('../../utils/auth')
const fmt = require('../../utils/format')

const STORAGE_KEY = 'verifyRecords'

Page({
  data: {
    pointName: '图书馆西门自提点',
    todayCount: 0,
    orderNo: '',
    code: '',
    submitting: false,
    records: [],
    isMock: isMock()
  },

  onLoad() {
    if (!auth.requireLogin()) return
    this.restoreRecords()
  },

  onShow() {
    this.restoreRecords()
  },

  /* ---------------- 本地记录 ---------------- */

  restoreRecords() {
    const today = fmt.formatTime(new Date(), 'YYYY-MM-DD')
    const saved = wx.getStorageSync(STORAGE_KEY) || {}
    const records = saved.date === today ? saved.list || [] : []
    this.setData({
      records: records,
      todayCount: records.length
    })
  },

  saveRecord(record) {
    const today = fmt.formatTime(new Date(), 'YYYY-MM-DD')
    const list = [record].concat(this.data.records).slice(0, 20)
    wx.setStorageSync(STORAGE_KEY, { date: today, list: list })
    this.setData({
      records: list,
      todayCount: list.length
    })
  },

  /* ---------------- 输入 ---------------- */

  onOrderNoInput(e) {
    this.setData({ orderNo: String(e.detail.value || '').trim() })
  },

  onCodeInput(e) {
    this.setData({ code: String(e.detail.value || '').trim() })
  },

  parseScan(result) {
    const text = String(result || '').trim()
    if (!text) return
    // 支持三种扫码内容：纯订单号 TW2026...、纯 6 位核销码、带参数的链接
    const orderMatch = text.match(/(TW\d{10,20})/i)
    if (orderMatch) {
      this.setData({ orderNo: orderMatch[1] })
    }
    const codeMatch = text.match(/(?:code=|核销码[:：]?\s*)(\d{6})/) || text.match(/^\d{6}$/)
    if (codeMatch) {
      this.setData({ code: codeMatch[1] })
    }
    if (!orderMatch && !codeMatch) {
      wx.showToast({ title: '没识别出订单号或核销码', icon: 'none' })
    }
  },

  onScan() {
    const that = this
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode', 'barCode'],
      success: function (res) {
        that.parseScan(res.result)
      },
      fail: function () {}
    })
  },

  /* ---------------- 核销 ---------------- */

  onSubmit() {
    const that = this
    const orderNo = String(this.data.orderNo || '').trim()
    const code = String(this.data.code || '').trim()
    if (!orderNo) {
      wx.showToast({ title: '请填写或扫描订单号', icon: 'none' })
      return
    }
    if (!/^\d{6}$/.test(code)) {
      wx.showToast({ title: '请输入 6 位核销码', icon: 'none' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true })

    request('/v1/orders/' + orderNo + '/verify', {
      method: 'POST',
      loading: true,
      loadingText: '核销中',
      data: { code: code }
    })
      .then(function (res) {
        that.setData({ submitting: false, orderNo: '', code: '' })
        const view = res || {}
        const listing = view.listing || {}
        that.saveRecord({
          orderNo: view.orderNo || orderNo,
          title: listing.title || '教材',
          amountText: view.amountText || '',
          buyer: view.counterpart || (view.buyer && view.buyer.nickname) || '',
          verifiedAtText: view.verifiedAtText || fmt.formatTime(new Date(), 'YYYY-MM-DD HH:mm')
        })
        wx.showModal({
          title: '核销成功',
          content:
            '订单 ' +
            (view.orderNo || orderNo) +
            '\n教材：' +
            (listing.title || '教材') +
            '\n金额：¥' +
            (view.amountText || '-') +
            '\n买家：' +
            ((view.buyer && view.buyer.nickname) || view.counterpart || '-') +
            '\n\n请把书交给买家，交易完成。',
          showCancel: false,
          confirmText: '完成',
          confirmColor: '#2f7d32'
        })
      })
      .catch(function () {
        that.setData({ submitting: false })
      })
  },

  copyOrderNo(e) {
    wx.setClipboardData({ data: e.currentTarget.dataset.no || '' })
  }
})
