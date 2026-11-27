/**
 * 本地假数据与假接口
 * USE_MOCK = true 时，utils/request.js 不会发真实网络请求，而是调用这里的 dispatch()
 * 返回结构与后端一致：{ code, message, data }
 *
 * 已覆盖的接口（与任务书一一对应）：
 *   POST /v1/auth/login | POST /v1/auth/register | GET /v1/auth/me
 *   GET  /v1/courses | GET /v1/courses/{id}/textbooks
 *   GET  /v1/textbooks | GET /v1/textbooks/{id}/price-index
 *   GET  /v1/listings | GET /v1/listings/{id} | POST /v1/listings
 *   GET  /v1/listings/mine | POST /v1/listings/{id}/favorite
 *   GET  /v1/pickup-points
 *   POST /v1/orders | POST /v1/orders/{orderNo}/pay | GET /v1/orders
 *   GET  /v1/orders/{orderNo} | POST /v1/orders/{orderNo}/verify
 *   POST /v1/reviews
 *   GET  /v1/group-buys/{inviteCode} | POST /v1/group-buys/{inviteCode}/join
 *   GET  /v1/messages/unread-count | GET /v1/messages
 */
const fmt = require('./format')

const USE_MOCK = true
const LATENCY = 300

class ApiError extends Error {
  constructor(code, message) {
    super(message)
    this.code = code
  }
}

/* ============================ 演示数据 ============================ */

const NOW = Date.now()
const MINUTE = 60 * 1000

function timeAgo(minutes) {
  return fmt.formatTime(new Date(NOW - minutes * MINUTE), 'YYYY-MM-DD HH:mm:ss')
}

function timeAfter(minutes) {
  return fmt.formatTime(new Date(NOW + minutes * MINUTE), 'YYYY-MM-DD HH:mm:ss')
}

const SCHOOL = { id: 1, name: '示例大学', city: '杭州', province: '浙江' }

const USERS = [
  {
    id: 1,
    account: 'admin',
    phone: '13800000000',
    password: 'admin123',
    nickname: '管理员',
    avatar: '',
    college: '',
    major: '',
    grade: '',
    dorm: '',
    studentNo: '',
    creditScore: 100,
    role: 'ADMIN',
    verifyState: 'VERIFIED'
  },
  {
    id: 2,
    account: 'buyer001',
    phone: '13800000001',
    password: '123456',
    nickname: '小黄同学',
    avatar: '',
    college: '计算机学院',
    major: '软件工程',
    grade: '2023',
    dorm: '3号楼302',
    studentNo: '2023010101',
    creditScore: 102,
    role: 'STUDENT',
    verifyState: 'VERIFIED'
  },
  {
    id: 3,
    account: 'seller001',
    phone: '13800000002',
    password: '123456',
    nickname: '学长清仓',
    avatar: '',
    college: '计算机学院',
    major: '软件工程',
    grade: '2020',
    dorm: '7号楼101',
    studentNo: '2020010202',
    creditScore: 108,
    role: 'STUDENT',
    verifyState: 'VERIFIED'
  },
  {
    id: 4,
    account: 'clerk001',
    phone: '13800000003',
    password: '123456',
    nickname: '三食堂店员',
    avatar: '',
    college: '',
    major: '',
    grade: '',
    dorm: '',
    studentNo: '',
    creditScore: 100,
    role: 'CLERK',
    verifyState: 'VERIFIED'
  },
  {
    id: 5,
    account: 'leader001',
    phone: '13800000004',
    password: '123456',
    nickname: '班委小张',
    avatar: '',
    college: '计算机学院',
    major: '软件工程',
    grade: '2023',
    dorm: '5号楼201',
    studentNo: '2023010303',
    creditScore: 105,
    role: 'LEADER',
    verifyState: 'VERIFIED'
  }
]

const COURSES = [
  { id: 1, schoolId: 1, code: 'MATH1001', name: '高等数学 A（上）', college: '数学学院', major: '通用', term: '2026-2027-1', teacher: '陈老师', credit: 5 },
  { id: 2, schoolId: 1, code: 'MATH1002', name: '线性代数', college: '数学学院', major: '通用', term: '2026-2027-1', teacher: '刘老师', credit: 3 },
  { id: 3, schoolId: 1, code: 'ENG1001', name: '大学英语 综合教程 2', college: '外国语学院', major: '通用', term: '2026-2027-1', teacher: '赵老师', credit: 4 },
  { id: 4, schoolId: 1, code: 'SE2001', name: '数据结构与算法', college: '计算机学院', major: '软件工程', term: '2026-2027-1', teacher: '周老师', credit: 4 },
  { id: 5, schoolId: 1, code: 'SE2002', name: '数据库系统原理', college: '计算机学院', major: '软件工程', term: '2026-2027-1', teacher: '吴老师', credit: 3.5 },
  { id: 6, schoolId: 1, code: 'SE2003', name: '计算机网络', college: '计算机学院', major: '软件工程', term: '2026-2027-1', teacher: '郑老师', credit: 3 }
]

const TEXTBOOKS = [
  { id: 1, isbn13: '9787040396638', title: '高等数学（第七版）上册', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第七版', publishYear: 2014, price: 45, category: '公共课' },
  { id: 2, isbn13: '9787040396645', title: '高等数学（第七版）下册', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第七版', publishYear: 2014, price: 42, category: '公共课' },
  { id: 3, isbn13: '9787040396607', title: '线性代数（第五版）', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第五版', publishYear: 2007, price: 32, category: '公共课' },
  { id: 4, isbn13: '9787040526998', title: '大学英语 综合教程 2（第3版）', author: '李荫华', publisher: '上海外语教育出版社', edition: '第三版', publishYear: 2019, price: 49, category: '公共课' },
  { id: 5, isbn13: '9787115547385', title: '数据结构（C语言版）', author: '严蔚敏', publisher: '人民邮电出版社', edition: '第二版', publishYear: 2021, price: 59, category: '专业核心' },
  { id: 6, isbn13: '9787040406542', title: '数据库系统概论（第5版）', author: '王珊', publisher: '高等教育出版社', edition: '第五版', publishYear: 2014, price: 42, category: '专业核心' },
  { id: 7, isbn13: '9787115412829', title: '计算机网络（第7版）', author: '谢希仁', publisher: '电子工业出版社', edition: '第七版', publishYear: 2017, price: 49, category: '专业核心' },
  { id: 8, isbn13: '9787040396652', title: '高等数学（第六版）上册', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第六版', publishYear: 2007, price: 38, category: '公共课' }
]

// 课程 - 教材映射：bindType REQUIRED 即「本校指定教材」
const COURSE_TEXTBOOKS = [
  { courseId: 1, textbookId: 1, bindType: 'REQUIRED', teacher: '陈老师', semester: '2026-2027-1' },
  { courseId: 1, textbookId: 8, bindType: 'REFERENCE', teacher: '陈老师', semester: '2026-2027-1' },
  { courseId: 2, textbookId: 3, bindType: 'REQUIRED', teacher: '刘老师', semester: '2026-2027-1' },
  { courseId: 3, textbookId: 4, bindType: 'REQUIRED', teacher: '赵老师', semester: '2026-2027-1' },
  { courseId: 4, textbookId: 5, bindType: 'REQUIRED', teacher: '周老师', semester: '2026-2027-1' },
  { courseId: 5, textbookId: 6, bindType: 'REQUIRED', teacher: '吴老师', semester: '2026-2027-1' },
  { courseId: 6, textbookId: 7, bindType: 'REQUIRED', teacher: '郑老师', semester: '2026-2027-1' }
]

const RAW_LISTINGS = [
  {
    id: 1, sellerId: 3, textbookId: 1, courseId: 1, title: '高等数学（第七版）上册', price: 15, originalPrice: 45,
    conditionLevel: 'LIKE_NEW', description: '9 成新，只在期末翻过，无笔记无划线。书角平整，可以当面验书。',
    dorm: '7号楼101', status: 'ON_SALE', viewCount: 46, favoriteCount: 5, distanceKm: 0.6, publishedAt: timeAgo(60 * 26)
  },
  {
    id: 2, sellerId: 3, textbookId: 3, courseId: 2, title: '线性代数（第五版）', price: 10, originalPrice: 32,
    conditionLevel: 'GOOD', description: '8 成新，前两章有铅笔笔记，可擦除。附带手写公式总结一张。',
    dorm: '7号楼101', status: 'ON_SALE', viewCount: 31, favoriteCount: 3, distanceKm: 0.6, publishedAt: timeAgo(60 * 25)
  },
  {
    id: 3, sellerId: 3, textbookId: 4, courseId: 3, title: '大学英语 综合教程 2（第3版）', price: 12, originalPrice: 49,
    conditionLevel: 'LIKE_NEW', description: '含听力二维码未刮开，配套光盘齐全。',
    dorm: '7号楼101', status: 'ON_SALE', viewCount: 22, favoriteCount: 2, distanceKm: 0.6, publishedAt: timeAgo(60 * 20)
  },
  {
    id: 4, sellerId: 5, textbookId: 5, courseId: 4, title: '数据结构（C语言版）', price: 20, originalPrice: 59,
    conditionLevel: 'GOOD', description: '有课堂笔记，字迹工整，重点章节用荧光笔标注。',
    dorm: '5号楼201', status: 'ON_SALE', viewCount: 18, favoriteCount: 1, distanceKm: 0.3, publishedAt: timeAgo(60 * 15)
  },
  {
    id: 5, sellerId: 5, textbookId: 6, courseId: 5, title: '数据库系统概论（第5版）', price: 14, originalPrice: 42,
    conditionLevel: 'FAIR', description: '有较多笔记与荧光笔标记，不影响阅读，介意慎拍。',
    dorm: '5号楼201', status: 'ON_SALE', viewCount: 12, favoriteCount: 0, distanceKm: 0.3, publishedAt: timeAgo(60 * 11)
  },
  {
    id: 6, sellerId: 2, textbookId: 1, courseId: 1, title: '高等数学（第七版）上册', price: 16, originalPrice: 45,
    conditionLevel: 'LIKE_NEW', description: '只翻过前 30 页，几乎全新，附赠课后习题答案打印版。',
    dorm: '3号楼302', status: 'ON_SALE', viewCount: 9, favoriteCount: 0, distanceKm: 0.2, publishedAt: timeAgo(60 * 8)
  },
  {
    id: 7, sellerId: 3, textbookId: 7, courseId: 6, title: '计算机网络（第7版）', price: 18, originalPrice: 49,
    conditionLevel: 'GOOD', description: '8 成新，书内有少量重点标注，无缺页。',
    dorm: '7号楼101', status: 'SOLD', viewCount: 27, favoriteCount: 4, distanceKm: 0.6, publishedAt: timeAgo(60 * 70)
  }
]

const PICKUP_POINTS = [
  { id: 1, schoolId: 1, name: '图书馆西门自提点', address: '图书馆西门一层（借还书处旁）', openTime: '12:00', closeTime: '21:00', capacity: 300, distance: 0.4, managerId: 4 },
  { id: 2, schoolId: 1, name: '三食堂东门自提点', address: '三食堂东门快递柜旁', openTime: '11:00', closeTime: '20:30', capacity: 200, distance: 0.8, managerId: 4 }
]

const ORDERS = [
  {
    orderNo: 'TW20261127090012', buyerId: 2, sellerId: 3, listingId: 1, textbookId: 1, pointId: 1,
    amount: 15, status: 'WAIT_VERIFY', payChannel: 'MOCK', tradeNo: 'MOCKPAY20261127090012',
    createdAt: timeAgo(48), paidAt: timeAgo(45), verifiedAt: null, settledAt: null,
    expireAt: timeAfter(10), reviewed: false, rating: 0, reviewContent: ''
  },
  {
    orderNo: 'TW20261127093033', buyerId: 2, sellerId: 3, listingId: 2, textbookId: 3, pointId: 1,
    amount: 10, status: 'PENDING_PAY', payChannel: 'MOCK', tradeNo: '',
    createdAt: timeAgo(3), paidAt: null, verifiedAt: null, settledAt: null,
    expireAt: timeAfter(12), reviewed: false, rating: 0, reviewContent: ''
  },
  {
    orderNo: 'TW20261126183045', buyerId: 2, sellerId: 5, listingId: 4, textbookId: 5, pointId: 2,
    amount: 20, status: 'VERIFIED', payChannel: 'MOCK', tradeNo: 'MOCKPAY20261126183045',
    createdAt: timeAgo(60 * 18), paidAt: timeAgo(60 * 18 - 2), verifiedAt: timeAgo(95), settledAt: null,
    expireAt: timeAgo(60 * 18 - 15), reviewed: false, rating: 0, reviewContent: ''
  },
  {
    orderNo: 'TW20261125101508', buyerId: 2, sellerId: 3, listingId: 3, textbookId: 4, pointId: 1,
    amount: 12, status: 'SETTLED', payChannel: 'MOCK', tradeNo: 'MOCKPAY20261125101508',
    createdAt: timeAgo(60 * 50), paidAt: timeAgo(60 * 50 - 2), verifiedAt: timeAgo(60 * 44), settledAt: timeAgo(60 * 20),
    expireAt: timeAgo(60 * 50 - 15), reviewed: true, rating: 5, reviewContent: '书和描述一致，卖家人很好，自提点就在图书馆，顺路就取了。'
  },
  {
    orderNo: 'TW20261127103066', buyerId: 5, sellerId: 2, listingId: 6, textbookId: 1, pointId: 2,
    amount: 16, status: 'WAIT_VERIFY', payChannel: 'MOCK', tradeNo: 'MOCKPAY20261127103066',
    createdAt: timeAgo(26), paidAt: timeAgo(24), verifiedAt: null, settledAt: null,
    expireAt: timeAfter(10), reviewed: false, rating: 0, reviewContent: ''
  }
]

const GROUP_BUY = {
  id: 1,
  courseId: 1,
  textbookId: 1,
  leaderId: 5,
  title: '高数A（上）第七版 · 三班教材接龙',
  targetCount: 40,
  unitPrice: 15,
  inviteCode: 'MATH7A',
  currentCount: 6,
  deadline: fmt.formatTime(new Date(NOW + 18 * 24 * 60 * MINUTE), 'YYYY-MM-DD HH:mm:ss'),
  status: 'OPEN',
  commissionRate: 0.1,
  participants: [
    { userId: 5, nickname: '班委小张', quantity: 1, contact: '微信 zhangtong', joinedAt: timeAgo(60 * 30), isLeader: true },
    { userId: 2, nickname: '小黄同学', quantity: 2, contact: '微信 huangyj', joinedAt: timeAgo(60 * 6), isLeader: false },
    { userId: 3, nickname: '学长清仓', quantity: 1, contact: '微信 limo', joinedAt: timeAgo(60 * 4), isLeader: false },
    { userId: 5, nickname: '匿名同学', quantity: 2, contact: '微信 x***', joinedAt: timeAgo(60 * 2), isLeader: false }
  ]
}

const MESSAGES = [
  { id: 1, type: 'ORDER', title: '订单已支付，等待到店核销', content: '订单 TW20261127090012 已支付 ¥15.00，请在 24 小时内到图书馆西门自提点出示核销码。', createdAt: timeAgo(45), read: false },
  { id: 2, type: 'CREDIT', title: '信用分 +2', content: '上一笔交易已完成，你的信用分提升至 102，继续保持。', createdAt: timeAgo(60 * 20), read: false },
  { id: 3, type: 'SYSTEM', title: '同款教材价格提醒', content: '你收藏的《数据结构（C语言版）》近 7 天有 3 条新上架，最低 ¥18.00。', createdAt: timeAgo(60 * 26), read: false }
]

/* ============================ 内存状态 ============================ */

const STATE = {
  listings: RAW_LISTINGS.map(function (item) {
    return Object.assign({}, item)
  }),
  orders: ORDERS.map(function (item) {
    return Object.assign({}, item)
  }),
  favorites: [1, 4],
  groupBuy: GROUP_BUY,
  messages: MESSAGES,
  seq: 100,
  verifiedToday: 0
}

/* ============================ 工具函数 ============================ */

function nextId() {
  STATE.seq += 1
  return STATE.seq
}

function userById(id) {
  return USERS.filter(function (u) {
    return u.id === Number(id)
  })[0]
}

function publicUser(user) {
  if (!user) return null
  return {
    id: user.id,
    account: user.account,
    nickname: user.nickname,
    avatar: user.avatar,
    phone: user.phone,
    college: user.college,
    major: user.major,
    grade: user.grade,
    dorm: user.dorm,
    studentNo: user.studentNo,
    creditScore: user.creditScore,
    role: user.role,
    verifyState: user.verifyState,
    verifyStateText: user.verifyState === 'VERIFIED' ? '已认证' : '未认证'
  }
}

function currentUser(ctx) {
  if (!ctx.token) throw new ApiError(401, '请先登录')
  const account = String(ctx.token).replace('mock-token-', '')
  const user = USERS.filter(function (u) {
    return u.account === account
  })[0]
  if (!user) throw new ApiError(401, '登录状态已失效，请重新登录')
  return user
}

function optionalUser(ctx) {
  if (!ctx.token) return null
  const account = String(ctx.token).replace('mock-token-', '')
  return (
    USERS.filter(function (u) {
      return u.account === account
    })[0] || null
  )
}

function courseById(id) {
  return COURSES.filter(function (c) {
    return c.id === Number(id)
  })[0]
}

function textbookById(id) {
  return TEXTBOOKS.filter(function (t) {
    return t.id === Number(id)
  })[0]
}

function pointById(id) {
  return PICKUP_POINTS.filter(function (p) {
    return p.id === Number(id)
  })[0]
}

function listingById(id) {
  return STATE.listings.filter(function (l) {
    return l.id === Number(id)
  })[0]
}

function orderByNo(orderNo) {
  return STATE.orders.filter(function (o) {
    return o.orderNo === orderNo
  })[0]
}

function sellerOf(listing) {
  const user = userById(listing.sellerId) || {}
  return {
    id: user.id,
    nickname: user.nickname,
    avatar: user.avatar,
    college: user.college,
    dorm: user.dorm,
    creditScore: user.creditScore,
    verifyState: user.verifyState
  }
}

function listingView(listing) {
  if (!listing) return null
  const tb = textbookById(listing.textbookId) || {}
  const course = courseById(listing.courseId)
  const bind = COURSE_TEXTBOOKS.filter(function (m) {
    return m.courseId === listing.courseId && m.textbookId === listing.textbookId
  })[0]
  return Object.assign({}, listing, {
    edition: tb.edition || '',
    isbn13: tb.isbn13 || '',
    author: tb.author || '',
    publisher: tb.publisher || '',
    category: tb.category || '',
    textbookPrice: tb.price || 0,
    coverUrl: '',
    images: [],
    // 版本校验：课程-教材映射为 REQUIRED 时，打「本校指定教材」标识
    schoolRequired: !!bind && bind.bindType === 'REQUIRED',
    bindType: bind ? bind.bindType : '',
    teacher: bind ? bind.teacher : '',
    course: course ? { id: course.id, name: course.name, code: course.code, teacher: course.teacher } : null,
    seller: sellerOf(listing),
    distanceText: fmt.distanceText(listing.distanceKm),
    conditionText: fmt.conditionText(listing.conditionLevel),
    priceText: fmt.formatPrice(listing.price),
    originalPriceText: fmt.formatPrice(listing.originalPrice)
  })
}

function priceIndexOf(textbookId) {
  const prices = STATE.listings
    .filter(function (l) {
      return l.textbookId === Number(textbookId) && l.status === 'ON_SALE'
    })
    .map(function (l) {
      return Number(l.price)
    })
  if (!prices.length) {
    return { textbookId: Number(textbookId), min: 0, max: 0, avg: 0, count: 0 }
  }
  let sum = 0
  prices.forEach(function (p) {
    sum += p
  })
  return {
    textbookId: Number(textbookId),
    min: Math.min.apply(null, prices),
    max: Math.max.apply(null, prices),
    avg: Math.round((sum / prices.length) * 100) / 100,
    count: prices.length
  }
}

/**
 * 核销码：按 30 秒时间片轮换，页面上倒计时归零后重新拉取即可拿到新码
 * 与后端 biz_orders.verify_code（HMAC 生成，30s 轮换）的设计保持一致
 */
function verifyCodeFor(orderNo, offsetSlot) {
  const slot = Math.floor(Date.now() / 30000) + (offsetSlot || 0)
  const seed = orderNo + '#' + slot
  let hash = 7
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) % 900000
  }
  return String(100000 + hash)
}

function secondsToRefresh() {
  return 30 - (Math.floor(Date.now() / 1000) % 30)
}

function buildTimeline(order) {
  const steps = [
    { key: 'created', label: '提交订单', time: order.createdAt, desc: '订单已创建，请在 15 分钟内完成支付' },
    { key: 'paid', label: '支付成功 · 资金冻结', time: order.paidAt, desc: '平台担保交易，确认核销后才结算给卖家' },
    { key: 'verified', label: '到店核销', time: order.verifiedAt, desc: '携带教材到自提点出示核销码' },
    { key: 'settled', label: '交易完成', time: order.settledAt, desc: 'T+1 结算给卖家，双方信用分 +2' }
  ]
  return steps.map(function (step) {
    return {
      key: step.key,
      label: step.label,
      desc: step.desc,
      done: !!step.time,
      timeText: step.time ? fmt.formatTime(step.time, 'MM-DD HH:mm') : ''
    }
  })
}

function orderView(order, viewer) {
  const listing = listingById(order.listingId)
  const point = pointById(order.pointId)
  const buyer = userById(order.buyerId)
  const seller = userById(order.sellerId)
  const isBuyer = viewer ? viewer.id === order.buyerId : true
  const waitVerify = order.status === 'WAIT_VERIFY' || order.status === 'FROZEN'
  return {
    orderNo: order.orderNo,
    status: order.status,
    statusText: fmt.orderStatusText(order.status),
    statusTone: fmt.orderStatusTone(order.status),
    amount: order.amount,
    amountText: fmt.formatPrice(order.amount),
    createdAt: order.createdAt,
    createdAtText: fmt.formatTime(order.createdAt, 'YYYY-MM-DD HH:mm'),
    paidAtText: order.paidAt ? fmt.formatTime(order.paidAt, 'YYYY-MM-DD HH:mm') : '',
    verifiedAtText: order.verifiedAt ? fmt.formatTime(order.verifiedAt, 'YYYY-MM-DD HH:mm') : '',
    expireAt: order.expireAt,
    expireAtText: fmt.formatTime(order.expireAt, 'HH:mm'),
    payChannel: order.payChannel,
    tradeNo: order.tradeNo,
    // 核销码只在待核销状态返回，且 30 秒轮换
    verifyCode: waitVerify ? verifyCodeFor(order.orderNo, 0) : '',
    codeSeconds: secondsToRefresh(),
    listing: listing
      ? {
          id: listing.id,
          title: listing.title,
          price: listing.price,
          priceText: fmt.formatPrice(listing.price),
          conditionLevel: listing.conditionLevel,
          conditionText: fmt.conditionText(listing.conditionLevel),
          edition: (textbookById(listing.textbookId) || {}).edition || '',
          dorm: listing.dorm
        }
      : null,
    point: point
      ? {
          id: point.id,
          name: point.name,
          address: point.address,
          openTime: point.openTime,
          closeTime: point.closeTime
        }
      : null,
    buyer: buyer ? { id: buyer.id, nickname: buyer.nickname, creditScore: buyer.creditScore } : null,
    seller: seller ? { id: seller.id, nickname: seller.nickname, creditScore: seller.creditScore } : null,
    counterpart: isBuyer ? (seller ? seller.nickname : '卖家') : buyer ? buyer.nickname : '买家',
    role: isBuyer ? 'buyer' : 'seller',
    timeline: buildTimeline(order),
    reviewed: order.reviewed,
    rating: order.rating,
    reviewContent: order.reviewContent,
    canReview: isBuyer && (order.status === 'VERIFIED' || order.status === 'SETTLED') && !order.reviewed,
    canPay: order.status === 'PENDING_PAY' && isBuyer
  }
}

function genOrderNo() {
  const d = new Date()
  const stamp =
    d.getFullYear() +
    fmt.formatTime(d, 'MMDDHHmmss')
  return 'TW' + stamp + String(Math.floor(Math.random() * 900000) + 100000)
}

/* ============================ 接口实现 ============================ */

function login(ctx) {
  const body = ctx.body || {}
  const account = String(body.account || '').trim()
  const password = String(body.password || '')
  if (!account || !password) throw new ApiError(400, '请输入账号和密码')
  const user = USERS.filter(function (u) {
    return u.account === account || u.phone === account
  })[0]
  if (!user || user.password !== password) throw new ApiError(400, '账号或密码不正确')
  return { token: 'mock-token-' + user.account, user: publicUser(user) }
}

function register(ctx) {
  const body = ctx.body || {}
  const phone = String(body.phone || '').trim()
  const password = String(body.password || '')
  const nickname = String(body.nickname || '').trim()
  if (!/^1\d{10}$/.test(phone)) throw new ApiError(400, '请输入 11 位手机号')
  if (password.length < 6) throw new ApiError(400, '密码至少 6 位')
  if (!nickname) throw new ApiError(400, '请填写昵称')
  const exists = USERS.filter(function (u) {
    return u.phone === phone
  })[0]
  if (exists) throw new ApiError(400, '该手机号已注册，请直接登录')
  const user = {
    id: nextId(),
    account: phone,
    phone: phone,
    password: password,
    nickname: nickname,
    avatar: '',
    college: '',
    major: '',
    grade: '',
    dorm: '',
    studentNo: '',
    creditScore: 100,
    role: 'STUDENT',
    verifyState: 'UNVERIFIED'
  }
  USERS.push(user)
  return { token: 'mock-token-' + user.account, user: publicUser(user) }
}

function me(ctx) {
  return publicUser(currentUser(ctx))
}

function listCourses(ctx) {
  const keyword = String(ctx.query.keyword || '').trim()
  return COURSES.filter(function (c) {
    if (!keyword) return true
    return matchKeyword([c.name, c.code, c.teacher, c.major], keyword)
  }).map(function (c) {
    const bindings = COURSE_TEXTBOOKS.filter(function (m) {
      return m.courseId === c.id
    })
    const required = bindings.filter(function (m) {
      return m.bindType === 'REQUIRED'
    })[0]
    const tb = required ? textbookById(required.textbookId) : null
    return Object.assign({}, c, {
      textbookCount: bindings.length,
      requiredTextbookId: tb ? tb.id : 0,
      requiredTextbookTitle: tb ? tb.title : '',
      status: 'ENABLED'
    })
  })
}

function courseTextbooks(ctx, courseId) {
  const course = courseById(courseId)
  if (!course) throw new ApiError(404, '课程不存在')
  return COURSE_TEXTBOOKS.filter(function (m) {
    return m.courseId === courseId
  }).map(function (m) {
    const tb = textbookById(m.textbookId) || {}
    return {
      id: tb.id,
      textbookId: tb.id,
      courseId: course.id,
      courseName: course.name,
      title: tb.title,
      edition: tb.edition,
      author: tb.author,
      publisher: tb.publisher,
      isbn13: tb.isbn13,
      price: tb.price,
      category: tb.category,
      bindType: m.bindType,
      bindTypeText: fmt.bindTypeText(m.bindType),
      teacher: m.teacher,
      semester: m.semester
    }
  })
}

// 学生常用的口语简称 -> 课程/书名全称，让搜索「高数」「线代」也能命中
const KEYWORD_ALIAS = {
  高数: '高等数学',
  线代: '线性代数',
  大英: '大学英语',
  英语: '大学英语',
  计网: '计算机网络',
  概统: '概率论',
  概率: '概率论',
  数构: '数据结构'
}

function matchKeyword(values, keyword) {
  const candidates = [keyword]
  const alias = KEYWORD_ALIAS[keyword]
  if (alias) candidates.push(alias)
  return candidates.some(function (kw) {
    const lower = kw.toLowerCase()
    return values.some(function (value) {
      return String(value || '').toLowerCase().indexOf(lower) >= 0
    })
  })
}

function listTextbooks(ctx) {
  const keyword = String(ctx.query.keyword || '').trim()
  const isbn = String(ctx.query.isbn || '').trim()
  return TEXTBOOKS.filter(function (t) {
    if (isbn) return t.isbn13 === isbn || t.isbn13.indexOf(isbn) === 0
    if (!keyword) return true
    return matchKeyword([t.title, t.author, t.publisher, t.isbn13], keyword)
  }).map(function (t) {
    const links = COURSE_TEXTBOOKS.filter(function (m) {
      return m.textbookId === t.id
    })
    const required = links.filter(function (m) {
      return m.bindType === 'REQUIRED'
    })[0]
    const course = required ? courseById(required.courseId) : null
    return Object.assign({}, t, {
      courseId: course ? course.id : 0,
      courseName: course ? course.name : '',
      bindType: required ? 'REQUIRED' : 'REFERENCE',
      priceText: fmt.formatPrice(t.price)
    })
  })
}

function priceIndex(ctx, textbookId) {
  if (!textbookById(textbookId)) throw new ApiError(404, '教材不存在')
  return priceIndexOf(textbookId)
}

function listListings(ctx) {
  const q = ctx.query
  const keyword = String(q.keyword || '').trim()
  const courseId = q.courseId ? Number(q.courseId) : 0
  const textbookId = q.textbookId ? Number(q.textbookId) : 0
  const conditionLevel = String(q.conditionLevel || '').trim()
  const sort = String(q.sort || 'recommend')
  const page = Math.max(1, Number(q.page || 1))
  const size = Math.max(1, Number(q.size || 10))

  let list = STATE.listings.filter(function (l) {
    return l.status === 'ON_SALE'
  })
  if (keyword) {
    list = list.filter(function (l) {
      const tb = textbookById(l.textbookId) || {}
      return matchKeyword([l.title, tb.title, tb.isbn13, tb.author], keyword)
    })
  }
  if (courseId) {
    list = list.filter(function (l) {
      return l.courseId === courseId
    })
  }
  if (textbookId) {
    list = list.filter(function (l) {
      return l.textbookId === textbookId
    })
  }
  if (conditionLevel) {
    list = list.filter(function (l) {
      return l.conditionLevel === conditionLevel
    })
  }

  if (sort === 'price_asc') {
    list = list.sort(function (a, b) {
      return a.price - b.price
    })
  } else if (sort === 'price_desc') {
    list = list.sort(function (a, b) {
      return b.price - a.price
    })
  } else if (sort === 'latest') {
    list = list.sort(function (a, b) {
      return new Date(b.publishedAt.replace(/-/g, '/')) - new Date(a.publishedAt.replace(/-/g, '/'))
    })
  } else if (sort === 'distance') {
    list = list.sort(function (a, b) {
      return a.distanceKm - b.distanceKm
    })
  } else {
    // 综合：收藏 > 浏览 > 距离
    list = list.sort(function (a, b) {
      const scoreA = a.favoriteCount * 3 + a.viewCount * 0.2 - a.distanceKm * 5
      const scoreB = b.favoriteCount * 3 + b.viewCount * 0.2 - b.distanceKm * 5
      return scoreB - scoreA
    })
  }

  const total = list.length
  const start = (page - 1) * size
  const records = list.slice(start, start + size).map(listingView)
  return { records: records, total: total, page: page, size: size }
}

function listingDetail(ctx, id) {
  const listing = listingById(id)
  if (!listing) throw new ApiError(404, '教材不存在或已下架')
  listing.viewCount += 1
  const detail = listingView(listing)
  const index = priceIndexOf(listing.textbookId)
  detail.priceIndex = index
  detail.favorite = STATE.favorites.indexOf(listing.id) >= 0
  detail.viewCount = listing.viewCount
  return detail
}

function myListings(ctx) {
  const user = currentUser(ctx)
  const status = String(ctx.query.status || '').trim()
  let list = STATE.listings.filter(function (l) {
    return l.sellerId === user.id
  })
  if (status) {
    const group = { WAIT_VERIFY: ['LOCKED'], SOLD: ['SOLD'], ON_SALE: ['ON_SALE'], OFFLINE: ['OFFLINE'] }[status]
    if (group) {
      list = list.filter(function (l) {
        return group.indexOf(l.status) >= 0
      })
    } else {
      list = list.filter(function (l) {
        return l.status === status
      })
    }
  }
  return list.map(function (l) {
    const view = listingView(l)
    view.statusText = fmt.listingStatusText(l.status)
    return view
  })
}

function createListing(ctx) {
  const user = currentUser(ctx)
  const body = ctx.body || {}
  const textbook = textbookById(body.textbookId)
  if (!textbook) throw new ApiError(400, '请先选择教材')
  const price = Number(body.price)
  if (!price || price <= 0) throw new ApiError(400, '请填写正确的售价')
  if (price > textbook.price) throw new ApiError(400, '售价不应高于定价 ¥' + fmt.formatPrice(textbook.price))
  if (!body.conditionLevel) throw new ApiError(400, '请选择品相')

  const id = nextId()
  const listing = {
    id: id,
    sellerId: user.id,
    textbookId: textbook.id,
    courseId: Number(body.courseId || 0),
    title: textbook.title,
    price: price,
    originalPrice: textbook.price,
    conditionLevel: body.conditionLevel,
    description: String(body.description || ''),
    dorm: String(body.dorm || user.dorm || ''),
    status: 'ON_SALE',
    viewCount: 0,
    favoriteCount: 0,
    distanceKm: 0.2,
    publishedAt: fmt.formatTime(new Date(), 'YYYY-MM-DD HH:mm:ss')
  }
  STATE.listings.unshift(listing)
  return { id: id, listing: listingView(listing) }
}

function toggleFavorite(ctx, id) {
  currentUser(ctx)
  const listing = listingById(id)
  if (!listing) throw new ApiError(404, '教材不存在')
  const index = STATE.favorites.indexOf(listing.id)
  let favorited = false
  if (index >= 0) {
    STATE.favorites.splice(index, 1)
    listing.favoriteCount = Math.max(0, listing.favoriteCount - 1)
    favorited = false
  } else {
    STATE.favorites.push(listing.id)
    listing.favoriteCount += 1
    favorited = true
  }
  return { favorited: favorited, favoriteCount: listing.favoriteCount }
}

function pickupPoints(ctx) {
  const schoolId = Number(ctx.query.schoolId || 1)
  return PICKUP_POINTS.filter(function (p) {
    return p.schoolId === schoolId
  }).map(function (p) {
    return Object.assign({}, p, { distanceText: fmt.distanceText(p.distance) })
  })
}

function createOrder(ctx) {
  const user = currentUser(ctx)
  const body = ctx.body || {}
  if (!body.requestId) throw new ApiError(400, '缺少请求幂等号 requestId')

  // 幂等：同一 requestId 重复提交返回同一笔订单
  const existed = STATE.orders.filter(function (o) {
    return o.requestId === body.requestId
  })[0]
  if (existed) {
    return { orderNo: existed.orderNo, amount: existed.amount, expireAt: existed.expireAt }
  }

  const listing = listingById(body.listingId)
  if (!listing) throw new ApiError(404, '教材不存在')
  if (listing.status !== 'ON_SALE') throw new ApiError(400, '该教材已被锁定或售出')
  if (listing.sellerId === user.id) throw new ApiError(400, '不能购买自己上架的教材')
  const point = pointById(body.pointId)
  if (!point) throw new ApiError(400, '请选择自提点')

  const order = {
    orderNo: genOrderNo(),
    requestId: body.requestId,
    buyerId: user.id,
    sellerId: listing.sellerId,
    listingId: listing.id,
    textbookId: listing.textbookId,
    pointId: point.id,
    amount: listing.price,
    status: 'PENDING_PAY',
    payChannel: 'MOCK',
    tradeNo: '',
    createdAt: fmt.formatTime(new Date(), 'YYYY-MM-DD HH:mm:ss'),
    paidAt: null,
    verifiedAt: null,
    settledAt: null,
    expireAt: timeAfter(15),
    reviewed: false,
    rating: 0,
    reviewContent: ''
  }
  listing.status = 'LOCKED'
  STATE.orders.unshift(order)
  return { orderNo: order.orderNo, amount: order.amount, expireAt: order.expireAt }
}

function payOrder(ctx, orderNo) {
  const user = currentUser(ctx)
  const order = orderByNo(orderNo)
  if (!order) throw new ApiError(404, '订单不存在')
  if (order.buyerId !== user.id) throw new ApiError(403, '无权操作该订单')
  if (order.status === 'WAIT_VERIFY' || order.status === 'FROZEN') {
    return orderView(order, user)
  }
  if (order.status !== 'PENDING_PAY') throw new ApiError(400, '订单当前状态不可支付')
  const channel = (ctx.body && ctx.body.channel) || 'MOCK'
  order.status = 'WAIT_VERIFY'
  order.payChannel = channel
  order.tradeNo = 'MOCKPAY' + order.orderNo.slice(2)
  order.paidAt = fmt.formatTime(new Date(), 'YYYY-MM-DD HH:mm:ss')
  return orderView(order, user)
}

function listOrders(ctx) {
  const user = currentUser(ctx)
  const role = String(ctx.query.role || 'buyer')
  const status = String(ctx.query.status || '').trim()

  let list = STATE.orders.filter(function (o) {
    return role === 'seller' ? o.sellerId === user.id : o.buyerId === user.id
  })

  if (status) {
    // 客户端用「待核销 / 已完成」两个分组，后端也可直接按单值状态查询
    const groups = {
      WAIT_VERIFY: ['FROZEN', 'WAIT_VERIFY'],
      VERIFIED: ['VERIFIED', 'SETTLED'],
      DONE: ['VERIFIED', 'SETTLED']
    }
    const group = groups[status]
    if (group) {
      list = list.filter(function (o) {
        return group.indexOf(o.status) >= 0
      })
    } else {
      list = list.filter(function (o) {
        return o.status === status
      })
    }
  }

  return list
    .sort(function (a, b) {
      return new Date(b.createdAt.replace(/-/g, '/')) - new Date(a.createdAt.replace(/-/g, '/'))
    })
    .map(function (o) {
      return orderView(o, user)
    })
}

function orderDetail(ctx, orderNo) {
  const user = currentUser(ctx)
  const order = orderByNo(orderNo)
  if (!order) throw new ApiError(404, '订单不存在')
  if (order.buyerId !== user.id && order.sellerId !== user.id) {
    throw new ApiError(403, '无权查看该订单')
  }
  return orderView(order, user)
}

function verifyOrder(ctx, orderNo) {
  const user = currentUser(ctx)
  // 真实后端会校验店员角色与所属自提点，这里演示环境放宽为「已登录即可核销」
  const order = orderByNo(orderNo)
  if (!order) throw new ApiError(404, '订单不存在')
  if (order.status !== 'WAIT_VERIFY' && order.status !== 'FROZEN') {
    throw new ApiError(400, '订单当前状态不可核销（' + fmt.orderStatusText(order.status) + '）')
  }
  const code = String((ctx.body && ctx.body.code) || '').trim()
  if (!code) throw new ApiError(400, '请输入核销码')
  const valid = [verifyCodeFor(orderNo, 0), verifyCodeFor(orderNo, -1), '888888']
  if (valid.indexOf(code) < 0) {
    throw new ApiError(400, '核销码无效或已过期，请让买家刷新后重试')
  }
  order.status = 'VERIFIED'
  order.verifiedAt = fmt.formatTime(new Date(), 'YYYY-MM-DD HH:mm:ss')
  STATE.verifiedToday += 1
  const listing = listingById(order.listingId)
  if (listing) listing.status = 'SOLD'
  const view = orderView(order, user)
  view.operator = user.nickname
  view.verifiedToday = STATE.verifiedToday
  return view
}

function createReview(ctx) {
  const user = currentUser(ctx)
  const body = ctx.body || {}
  const order = orderByNo(body.orderNo)
  if (!order) throw new ApiError(404, '订单不存在')
  if (order.buyerId !== user.id) throw new ApiError(403, '只有买家可以评价')
  if (order.status !== 'VERIFIED' && order.status !== 'SETTLED') {
    throw new ApiError(400, '订单完成后才能评价')
  }
  if (order.reviewed) throw new ApiError(400, '该订单已评价')
  const rating = Number(body.rating || 5)
  if (rating < 1 || rating > 5) throw new ApiError(400, '评分需在 1-5 星之间')
  order.reviewed = true
  order.rating = rating
  order.reviewContent = String(body.content || '')
  order.reviewAnonymous = !!body.anonymous
  return { id: nextId(), orderNo: order.orderNo, rating: rating }
}

function groupBuyDetail(ctx, inviteCode) {
  const gb = STATE.groupBuy
  if (!gb || gb.inviteCode !== inviteCode) throw new ApiError(404, '接龙不存在或已结束')
  const user = optionalUser(ctx)
  const mine = user
    ? gb.participants.filter(function (p) {
        return p.userId === user.id
      })[0]
    : null
  const tb = textbookById(gb.textbookId) || {}
  const deadline = fmt.toDate(gb.deadline)
  const daysLeft = deadline ? Math.max(0, Math.ceil((deadline.getTime() - Date.now()) / (24 * 60 * 60 * 1000))) : 0
  return {
    id: gb.id,
    inviteCode: gb.inviteCode,
    title: gb.title,
    status: gb.status,
    statusText: gb.status === 'OPEN' ? '进行中' : '已结束',
    targetCount: gb.targetCount,
    currentCount: gb.currentCount,
    progress: Math.min(100, Math.round((gb.currentCount / gb.targetCount) * 100)),
    remainCount: Math.max(0, gb.targetCount - gb.currentCount),
    unitPrice: gb.unitPrice,
    unitPriceText: fmt.formatPrice(gb.unitPrice),
    deadlineText: fmt.formatTime(gb.deadline, 'MM月DD日 HH:mm'),
    daysLeft: daysLeft,
    textbookTitle: tb.title,
    textbookEdition: tb.edition,
    leader: { nickname: (userById(gb.leaderId) || {}).nickname || '团长' },
    participants: gb.participants,
    participantCount: gb.participants.length,
    joined: !!mine,
    myQuantity: mine ? mine.quantity : 0,
    totalQuantity: gb.participants.reduce(function (sum, p) {
      return sum + p.quantity
    }, 0)
  }
}

function joinGroupBuy(ctx, inviteCode) {
  const user = currentUser(ctx)
  const gb = STATE.groupBuy
  if (!gb || gb.inviteCode !== inviteCode) throw new ApiError(404, '接龙不存在或已结束')
  if (gb.status !== 'OPEN') throw new ApiError(400, '接龙已结束')
  const body = ctx.body || {}
  const quantity = Number(body.quantity || 1)
  if (!quantity || quantity < 1 || quantity > 10) throw new ApiError(400, '登记册数需在 1-10 之间')
  if (!String(body.contact || '').trim()) throw new ApiError(400, '请填写联系方式')

  const existing = gb.participants.filter(function (p) {
    return p.userId === user.id
  })[0]
  if (existing) {
    gb.currentCount += quantity - existing.quantity
    existing.quantity = quantity
    existing.contact = String(body.contact).trim()
  } else {
    gb.participants.push({
      userId: user.id,
      nickname: user.nickname,
      quantity: quantity,
      contact: String(body.contact).trim(),
      joinedAt: fmt.formatTime(new Date(), 'YYYY-MM-DD HH:mm:ss'),
      isLeader: false
    })
    gb.currentCount += quantity
  }
  return groupBuyDetail(ctx, inviteCode)
}

function unreadCount(ctx) {
  currentUser(ctx)
  const count = STATE.messages.filter(function (m) {
    return !m.read
  }).length
  return { count: count }
}

function messages(ctx) {
  currentUser(ctx)
  return STATE.messages.map(function (m) {
    return Object.assign({}, m, { timeText: fmt.relativeTime(m.createdAt) })
  })
}

/* ============================ 路由 ============================ */

function route(ctx) {
  const path = ctx.path
  const method = ctx.method
  let match = null

  if (path === '/v1/auth/login' && method === 'POST') return login(ctx)
  if (path === '/v1/auth/register' && method === 'POST') return register(ctx)
  if (path === '/v1/auth/me') return me(ctx)

  if (path === '/v1/courses') return listCourses(ctx)
  if ((match = path.match(/^\/v1\/courses\/(\d+)\/textbooks$/))) {
    return courseTextbooks(ctx, Number(match[1]))
  }
  if (path === '/v1/textbooks') return listTextbooks(ctx)
  if ((match = path.match(/^\/v1\/textbooks\/(\d+)\/price-index$/))) {
    return priceIndex(ctx, Number(match[1]))
  }

  if (path === '/v1/listings' && method === 'GET') return listListings(ctx)
  if (path === '/v1/listings' && method === 'POST') return createListing(ctx)
  if (path === '/v1/listings/mine') return myListings(ctx)
  if ((match = path.match(/^\/v1\/listings\/(\d+)\/favorite$/)) && method === 'POST') {
    return toggleFavorite(ctx, Number(match[1]))
  }
  if ((match = path.match(/^\/v1\/listings\/(\d+)$/)) && method === 'GET') {
    return listingDetail(ctx, Number(match[1]))
  }

  if (path === '/v1/pickup-points') return pickupPoints(ctx)

  if (path === '/v1/orders' && method === 'POST') return createOrder(ctx)
  if (path === '/v1/orders' && method === 'GET') return listOrders(ctx)
  if ((match = path.match(/^\/v1\/orders\/([A-Za-z0-9]+)\/pay$/)) && method === 'POST') {
    return payOrder(ctx, match[1])
  }
  if ((match = path.match(/^\/v1\/orders\/([A-Za-z0-9]+)\/verify$/)) && method === 'POST') {
    return verifyOrder(ctx, match[1])
  }
  if ((match = path.match(/^\/v1\/orders\/([A-Za-z0-9]+)$/)) && method === 'GET') {
    return orderDetail(ctx, match[1])
  }

  if (path === '/v1/reviews' && method === 'POST') return createReview(ctx)

  if ((match = path.match(/^\/v1\/group-buys\/([A-Za-z0-9]+)\/join$/)) && method === 'POST') {
    return joinGroupBuy(ctx, match[1])
  }
  if ((match = path.match(/^\/v1\/group-buys\/([A-Za-z0-9]+)$/)) && method === 'GET') {
    return groupBuyDetail(ctx, match[1])
  }

  if (path === '/v1/messages/unread-count') return unreadCount(ctx)
  if (path === '/v1/messages') return messages(ctx)

  throw new ApiError(404, '模拟接口不存在：' + method + ' ' + path)
}

function parseQuery(url) {
  const index = url.indexOf('?')
  const query = {}
  if (index < 0) return query
  url
    .slice(index + 1)
    .split('&')
    .forEach(function (pair) {
      if (!pair) return
      const eq = pair.indexOf('=')
      const key = decodeURIComponent(eq < 0 ? pair : pair.slice(0, eq))
      const value = eq < 0 ? '' : decodeURIComponent(pair.slice(eq + 1))
      query[key] = value
    })
  return query
}

function stripQuery(url) {
  const index = url.indexOf('?')
  return index < 0 ? url : url.slice(0, index)
}

/**
 * 供 request.js 调用，返回与后端一致的响应体
 */
function dispatch(options) {
  const url = options.url || ''
  return new Promise(function (resolve) {
    setTimeout(function () {
      try {
        const data = route({
          path: stripQuery(url),
          query: parseQuery(url),
          method: String(options.method || 'GET').toUpperCase(),
          body: options.data || {},
          token: options.token || ''
        })
        resolve({ code: 0, message: 'ok', data: data === undefined ? null : data })
      } catch (err) {
        if (err instanceof ApiError) {
          resolve({ code: err.code, message: err.message, data: null })
        } else {
          resolve({ code: 500, message: (err && err.message) || '本地模拟数据异常', data: null })
        }
      }
    }, LATENCY)
  })
}

module.exports = {
  USE_MOCK: USE_MOCK,
  LATENCY: LATENCY,
  dispatch: dispatch
}
