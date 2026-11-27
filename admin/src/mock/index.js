import dayjs from 'dayjs'

/**
 * 本地 Mock 层
 * 用途：VITE_USE_MOCK === 'true' 时把 axios 的 adapter 换成本地假数据，
 *      无需后端即可完整浏览所有界面与交互（增删改只在内存生效，刷新即还原）。
 * 约定：返回统一响应体 { code, message, data }；code !== 0 会被 request.js 弹出错误提示。
 */

const TOKEN = 'mock-token-admin-2026'
const ADMIN = { id: 1, nickname: '管理员', role: 'ADMIN' }

const COLORS = ['#4f7cff', '#3aa6a0', '#e08a3c', '#a05ac0', '#5b8c5a', '#c05656', '#4a6fa5', '#b07d3a']

function cover(text, index = 0) {
  const color = COLORS[index % COLORS.length]
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="104"><rect width="80" height="104" rx="4" fill="${color}"/><rect x="6" y="8" width="68" height="88" rx="3" fill="rgba(255,255,255,0.14)"/><text x="40" y="56" font-size="14" fill="#ffffff" text-anchor="middle" font-family="sans-serif">${text}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

// 商户资质图（横向营业执照占位图）
function license(text, index = 0) {
  const color = COLORS[index % COLORS.length]
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="100"><rect width="160" height="100" rx="6" fill="#fbfcff" stroke="${color}" stroke-width="2"/><rect x="10" y="10" width="140" height="80" rx="4" fill="${color}" fill-opacity="0.08"/><text x="80" y="46" font-size="14" fill="${color}" text-anchor="middle" font-family="sans-serif">${text}</text><text x="80" y="68" font-size="11" fill="#8a94a6" text-anchor="middle" font-family="sans-serif">营业执照（Mock）</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

const textbooks = [
  { id: 1, isbn13: '9787040396638', title: '高等数学（第七版）上册', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第七版', publishYear: 2014, price: 45.0, category: '公共课', coverUrl: cover('高数上', 0), status: 'ENABLED' },
  { id: 2, isbn13: '9787040396645', title: '高等数学（第七版）下册', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第七版', publishYear: 2014, price: 42.0, category: '公共课', coverUrl: cover('高数下', 1), status: 'ENABLED' },
  { id: 3, isbn13: '9787040396607', title: '线性代数（第五版）', author: '同济大学数学系', publisher: '高等教育出版社', edition: '第五版', publishYear: 2007, price: 32.0, category: '公共课', coverUrl: cover('线代', 2), status: 'ENABLED' },
  { id: 4, isbn13: '9787544650012', title: '大学英语 综合教程 2（第3版）', author: '李荫华', publisher: '上海外语教育出版社', edition: '第三版', publishYear: 2019, price: 49.0, category: '公共课', coverUrl: cover('英语2', 3), status: 'ENABLED' },
  { id: 5, isbn13: '9787115547385', title: '数据结构（C语言版）', author: '严蔚敏', publisher: '人民邮电出版社', edition: '第二版', publishYear: 2021, price: 59.0, category: '专业核心', coverUrl: cover('数据结构', 4), status: 'ENABLED' },
  { id: 6, isbn13: '9787040406542', title: '数据库系统概论（第5版）', author: '王珊', publisher: '高等教育出版社', edition: '第五版', publishYear: 2014, price: 42.0, category: '专业核心', coverUrl: cover('数据库', 5), status: 'ENABLED' },
  { id: 7, isbn13: '9787115412829', title: '计算机网络（第7版）', author: '谢希仁', publisher: '电子工业出版社', edition: '第七版', publishYear: 2017, price: 49.0, category: '专业核心', coverUrl: cover('计网', 6), status: 'ENABLED' },
  { id: 8, isbn13: '9787040519869', title: '概率论与数理统计（第四版）', author: '盛骤', publisher: '高等教育出版社', edition: '第四版', publishYear: 2019, price: 39.8, category: '公共课', coverUrl: cover('概率论', 7), status: 'ENABLED' },
  { id: 9, isbn13: '9787040445689', title: '大学物理学（第五版）上册', author: '马文蔚', publisher: '高等教育出版社', edition: '第五版', publishYear: 2014, price: 43.5, category: '公共课', coverUrl: cover('大学物理', 1), status: 'ENABLED' },
  { id: 10, isbn13: '9787040599008', title: '马克思主义基本原理（2023年版）', author: '本书编写组', publisher: '高等教育出版社', edition: '2023年版', publishYear: 2023, price: 26.0, category: '通识', coverUrl: cover('马原', 2), status: 'DISABLED' }
]

// 教材 -> 关联课程（展开行使用）
const courseMap = {
  1: [
    { id: 1, name: '高等数学 A（上）', teacher: '陈老师', bindType: 'REQUIRED', semester: '2026-2027-1' },
    { id: 2, name: '高等数学 B（上）', teacher: '刘老师', bindType: 'REFERENCE', semester: '2026-2027-1' }
  ],
  2: [{ id: 3, name: '高等数学 A（下）', teacher: '陈老师', bindType: 'REQUIRED', semester: '2026-2027-2' }],
  3: [{ id: 4, name: '线性代数', teacher: '刘老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  4: [{ id: 5, name: '大学英语 综合教程 2', teacher: '赵老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  5: [{ id: 6, name: '数据结构与算法', teacher: '周老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  6: [{ id: 7, name: '数据库系统原理', teacher: '吴老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  7: [{ id: 8, name: '计算机网络', teacher: '郑老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  8: [{ id: 9, name: '概率论与数理统计', teacher: '孙老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  9: [{ id: 10, name: '大学物理（上）', teacher: '钱老师', bindType: 'REQUIRED', semester: '2026-2027-1' }],
  10: [{ id: 11, name: '马克思主义基本原理', teacher: '李老师', bindType: 'REQUIRED', semester: '2026-2027-1' }]
}

const listings = [
  { id: 1, title: '高等数学（第七版）上册', seller: '学长清仓', price: 15.0, originalPrice: 45.0, condition: 'LIKE_NEW', status: 'PENDING', coverUrl: cover('高数上', 0), publishedAt: '2026-11-27 09:00:00' },
  { id: 2, title: '线性代数（第五版）', seller: '学长清仓', price: 10.0, originalPrice: 32.0, condition: 'GOOD', status: 'PENDING', coverUrl: cover('线代', 2), publishedAt: '2026-11-27 08:40:00' },
  { id: 3, title: '大学英语 综合教程 2（第3版）', seller: '学长清仓', price: 12.0, originalPrice: 49.0, condition: 'LIKE_NEW', status: 'PENDING', coverUrl: cover('英语2', 3), publishedAt: '2026-11-26 21:15:00' },
  { id: 4, title: '数据结构（C语言版）', seller: '班委小张', price: 20.0, originalPrice: 59.0, condition: 'GOOD', status: 'PENDING', coverUrl: cover('数据结构', 4), publishedAt: '2026-11-26 19:30:00' },
  { id: 5, title: '数据库系统概论（第5版）', seller: '班委小张', price: 14.0, originalPrice: 42.0, condition: 'FAIR', status: 'ON_SALE', coverUrl: cover('数据库', 5), publishedAt: '2026-11-25 16:00:00' },
  { id: 6, title: '高等数学（第七版）上册', seller: '小黄同学', price: 16.0, originalPrice: 45.0, condition: 'LIKE_NEW', status: 'ON_SALE', coverUrl: cover('高数上', 6), publishedAt: '2026-11-25 08:30:00' },
  { id: 7, title: '计算机网络（第7版）', seller: '陈思远', price: 18.0, originalPrice: 49.0, condition: 'GOOD', status: 'ON_SALE', coverUrl: cover('计网', 7), publishedAt: '2026-11-24 11:20:00' },
  { id: 8, title: '概率论与数理统计（第四版）', seller: '李小雨', price: 9.9, originalPrice: 39.8, condition: 'FAIR', status: 'REJECTED', coverUrl: cover('概率论', 1), publishedAt: '2026-11-23 14:05:00' },
  { id: 9, title: '大学物理学（第五版）上册', seller: '王浩然', price: 13.0, originalPrice: 43.5, condition: 'GOOD', status: 'SOLD', coverUrl: cover('大学物理', 2), publishedAt: '2026-11-22 10:00:00' },
  { id: 10, title: '马克思主义基本原理（2023年版）', seller: '赵一鸣', price: 8.0, originalPrice: 26.0, condition: 'FAIR', status: 'OFFLINE', coverUrl: cover('马原', 3), publishedAt: '2026-11-21 09:40:00' }
]

const ORDER_STATUS_TEXT = {
  PENDING_PAY: '待支付',
  WAIT_VERIFY: '待核销',
  COMPLETED: '已完成',
  REFUNDING: '退款中',
  REFUND_AGREED: '同意退款',
  REFUNDED: '已退款',
  ARBITRATING: '仲裁中',
  CLOSED: '已关闭'
}

// 按当前状态反推状态流转时间轴，让抽屉里的 el-timeline 有真实感
function timelineOf(createdAt, status) {
  const flows = {
    PENDING_PAY: ['PENDING_PAY'],
    WAIT_VERIFY: ['PENDING_PAY', 'WAIT_VERIFY'],
    COMPLETED: ['PENDING_PAY', 'WAIT_VERIFY', 'COMPLETED'],
    REFUNDING: ['PENDING_PAY', 'WAIT_VERIFY', 'REFUNDING'],
    REFUND_AGREED: ['PENDING_PAY', 'WAIT_VERIFY', 'REFUNDING', 'REFUND_AGREED'],
    REFUNDED: ['PENDING_PAY', 'WAIT_VERIFY', 'REFUNDING', 'REFUNDED'],
    ARBITRATING: ['PENDING_PAY', 'WAIT_VERIFY', 'REFUNDING', 'ARBITRATING'],
    CLOSED: ['PENDING_PAY', 'CLOSED']
  }
  const offsets = [0, 3, 60, 120]
  const flow = flows[status] || [status]
  const base = dayjs(createdAt)
  return flow.map((item, index) => ({
    status: item,
    text: ORDER_STATUS_TEXT[item] || item,
    time: base.add(offsets[index] || 0, 'minute').format('YYYY-MM-DD HH:mm:ss')
  }))
}

const rawOrders = [
  { id: 1, orderNo: 'TB20261127103001', buyerId: 2, buyer: '小黄同学', sellerId: 3, seller: '学长清仓', title: '高等数学（第七版）上册', amount: 15.0, status: 'PENDING_PAY', meetPlace: '图书馆西门自提点', meetTime: '2026-11-27 15:00:00', signPhotoUrl: '', completedAt: '', createdAt: '2026-11-27 10:30:00' },
  { id: 2, orderNo: 'TB20261126141502', buyerId: 6, buyer: '李小雨', sellerId: 5, seller: '班委小张', title: '大学英语 综合教程 2（第3版）', amount: 12.0, status: 'WAIT_VERIFY', meetPlace: '三食堂东门自提点', meetTime: '2026-11-26 18:00:00', signPhotoUrl: '', completedAt: '', createdAt: '2026-11-26 14:15:00' },
  { id: 3, orderNo: 'TB20261125102003', buyerId: 8, buyer: '陈思远', sellerId: 3, seller: '学长清仓', title: '线性代数（第五版）', amount: 10.0, status: 'WAIT_VERIFY', meetPlace: '图书馆西门自提点', meetTime: '2026-11-25 16:30:00', signPhotoUrl: '', completedAt: '', createdAt: '2026-11-25 10:20:00' },
  { id: 4, orderNo: 'TB20261124183004', buyerId: 2, buyer: '小黄同学', sellerId: 5, seller: '班委小张', title: '数据结构（C语言版）', amount: 20.0, status: 'COMPLETED', meetPlace: '三食堂东门自提点', meetTime: '2026-11-24 20:00:00', signPhotoUrl: cover('签收单', 4), completedAt: '2026-11-24 20:10:00', createdAt: '2026-11-24 18:30:00' },
  { id: 5, orderNo: 'TB20261123110005', buyerId: 9, buyer: '赵一鸣', sellerId: 3, seller: '学长清仓', title: '数据库系统概论（第5版）', amount: 14.0, status: 'COMPLETED', meetPlace: '图书馆西门自提点', meetTime: '2026-11-23 13:00:00', signPhotoUrl: cover('签收单', 5), completedAt: '2026-11-23 13:40:00', createdAt: '2026-11-23 11:00:00' },
  { id: 6, orderNo: 'TB20261122140006', buyerId: 6, buyer: '李小雨', sellerId: 2, seller: '小黄同学', title: '线性代数（第五版）', amount: 10.0, status: 'COMPLETED', meetPlace: '图书馆西门自提点', meetTime: '2026-11-22 16:00:00', signPhotoUrl: '', completedAt: '2026-11-22 16:20:00', createdAt: '2026-11-22 14:00:00' },
  { id: 7, orderNo: 'TB20261121101507', buyerId: 2, buyer: '小黄同学', sellerId: 8, seller: '陈思远', title: '大学英语 综合教程 2（第3版）', amount: 49.0, status: 'REFUNDED', meetPlace: '三食堂东门自提点', meetTime: '2026-11-21 16:00:00', signPhotoUrl: '', completedAt: '', refundReason: '收到书籍缺页，双方协商后平台退款', createdAt: '2026-11-21 10:15:00' },
  { id: 8, orderNo: 'TB20261120160008', buyerId: 7, buyer: '王浩然', sellerId: 3, seller: '学长清仓', title: '线性代数（第五版）', amount: 32.0, status: 'CLOSED', meetPlace: '图书馆西门自提点', meetTime: '2026-11-20 18:00:00', signPhotoUrl: '', completedAt: '', createdAt: '2026-11-20 16:00:00' },
  { id: 9, orderNo: 'TB20261119120009', buyerId: 8, buyer: '陈思远', sellerId: 2, seller: '小黄同学', title: '概率论与数理统计（第四版）', amount: 9.9, status: 'ARBITRATING', meetPlace: '三食堂东门自提点', meetTime: '2026-11-19 17:30:00', signPhotoUrl: '', completedAt: '', refundReason: '书页有大量水渍，与描述不符，卖家拒绝退货', createdAt: '2026-11-19 12:00:00' },
  { id: 10, orderNo: 'TB20261118092010', buyerId: 7, buyer: '王浩然', sellerId: 9, seller: '赵一鸣', title: '大学物理学（第五版）上册', amount: 13.0, status: 'REFUNDING', meetPlace: '图书馆西门自提点', meetTime: '2026-11-18 15:00:00', signPhotoUrl: '', completedAt: '', refundReason: '临时不需要了，申请退款', createdAt: '2026-11-18 09:20:00' },
  { id: 11, orderNo: 'TB20261117164011', buyerId: 9, buyer: '赵一鸣', sellerId: 8, seller: '陈思远', title: '计算机网络（第7版）', amount: 18.0, status: 'ARBITRATING', meetPlace: '三食堂东门自提点', meetTime: '2026-11-17 19:00:00', signPhotoUrl: '', completedAt: '', refundReason: '封面与描述不符，卖家拒绝退款', createdAt: '2026-11-17 16:40:00' },
  { id: 12, orderNo: 'TB20261116110512', buyerId: 6, buyer: '李小雨', sellerId: 9, seller: '赵一鸣', title: '马克思主义基本原理（2023年版）', amount: 8.0, status: 'REFUND_AGREED', meetPlace: '图书馆西门自提点', meetTime: '2026-11-16 14:00:00', signPhotoUrl: '', completedAt: '', refundReason: '卖家发错版本', createdAt: '2026-11-16 11:05:00' },
  { id: 13, orderNo: 'TB20261115153013', buyerId: 5, buyer: '班委小张', sellerId: 6, seller: '李小雨', title: '大学英语 综合教程 2（第3版）', amount: 12.0, status: 'REFUNDED', meetPlace: '三食堂东门自提点', meetTime: '2026-11-15 18:00:00', signPhotoUrl: '', completedAt: '', refundReason: '卖家未按约定时间面交', createdAt: '2026-11-15 15:30:00' }
]

// 退款单：status -> PENDING / AGREED / REJECTED / ARBITRATING / ARBITRATED
const refunds = [
  { id: 1, orderId: 9, applicantId: 8, reason: '书页有大量水渍，与描述不符，卖家拒绝退货', evidenceUrl: cover('水渍', 5), amount: 9.9, status: 'ARBITRATING', platformVerdict: '', platformRemark: '', createdAt: '2026-11-19 18:20:00' },
  { id: 2, orderId: 10, applicantId: 7, reason: '临时不需要了，申请退款', evidenceUrl: cover('退款', 2), amount: 13.0, status: 'PENDING', platformVerdict: '', platformRemark: '', createdAt: '2026-11-18 12:00:00' },
  { id: 3, orderId: 11, applicantId: 9, reason: '封面与描述不符，卖家拒绝退款', evidenceUrl: cover('封面', 1), amount: 18.0, status: 'ARBITRATING', platformVerdict: '', platformRemark: '', createdAt: '2026-11-17 20:10:00' },
  { id: 4, orderId: 12, applicantId: 6, reason: '卖家发错版本', evidenceUrl: '', amount: 8.0, status: 'AGREED', platformVerdict: '', platformRemark: '', createdAt: '2026-11-16 14:00:00' },
  { id: 5, orderId: 13, applicantId: 5, reason: '卖家未按约定时间面交', evidenceUrl: cover('面交', 3), amount: 12.0, status: 'ARBITRATED', platformVerdict: 'SELLER_FAULT', platformRemark: '卖家多次爽约，支持买家退款', decidedAt: '2026-11-16 10:00:00', refundedAt: '2026-11-16 10:05:00', createdAt: '2026-11-15 18:00:00' },
  { id: 6, orderId: 7, applicantId: 2, reason: '收到书籍缺页', evidenceUrl: cover('缺页', 4), amount: 49.0, status: 'ARBITRATED', platformVerdict: 'REFUNDED', platformRemark: '双方协商一致，已退款', decidedAt: '2026-11-22 09:30:00', refundedAt: '2026-11-22 09:35:00', createdAt: '2026-11-21 13:20:00' }
]

const orders = rawOrders.map((o) => ({ ...o, timeline: timelineOf(o.createdAt, o.status) }))

const merchants = [
  { id: 1, userId: 3, shopName: '学长清仓书屋', licenseUrl: license('学长清仓', 0), contact: '13800000002', status: 'PENDING', rejectReason: '', approvedBy: null, approvedAt: null, listingCount: 0, createdAt: '2026-11-27 09:12:00' },
  { id: 2, userId: 9, shopName: '一鸣二手书店', licenseUrl: license('赵一鸣', 1), contact: '13800000008', status: 'PENDING', rejectReason: '', approvedBy: null, approvedAt: null, listingCount: 0, createdAt: '2026-11-26 20:30:00' },
  { id: 3, userId: 8, shopName: '思远旧书铺', licenseUrl: license('陈思远', 2), contact: '13800000007', status: 'PENDING', rejectReason: '', approvedBy: null, approvedAt: null, listingCount: 0, createdAt: '2026-11-26 10:05:00' },
  { id: 4, userId: 10, shopName: '图书馆图书角', licenseUrl: license('图书角', 3), contact: '13800000009', status: 'APPROVED', rejectReason: '', approvedBy: 1, approvedAt: '2026-10-02 09:00:00', listingCount: 126, createdAt: '2026-10-01 08:30:00' },
  { id: 5, userId: 5, shopName: '班委教材代购', licenseUrl: license('班委小张', 4), contact: '13800000004', status: 'APPROVED', rejectReason: '', approvedBy: 1, approvedAt: '2026-09-08 10:00:00', listingCount: 48, createdAt: '2026-09-07 14:00:00' },
  { id: 6, userId: 6, shopName: '小雨书屋', licenseUrl: license('李小雨', 5), contact: '13800000005', status: 'REJECTED', rejectReason: '营业执照照片模糊，店铺名称与证件不一致', approvedBy: 1, approvedAt: '2026-11-20 11:00:00', listingCount: 0, createdAt: '2026-11-19 16:20:00' },
  { id: 7, userId: 7, shopName: '浩然书摊', licenseUrl: license('王浩然', 6), contact: '13800000006', status: 'FROZEN', rejectReason: '多次发布违规教材，暂停营业', approvedBy: 1, approvedAt: '2026-10-19 09:00:00', listingCount: 12, createdAt: '2026-10-18 14:40:00' }
]

const users = [
  { id: 1, nickname: '管理员', studentNo: '—', college: '信息中心', creditScore: 100, role: 'ADMIN', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000000', createdAt: '2026-09-01 08:00:00' },
  { id: 2, nickname: '小黄同学', studentNo: '2023010101', college: '计算机学院', creditScore: 102, role: 'STUDENT', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000001', createdAt: '2026-09-05 10:20:00' },
  { id: 3, nickname: '学长清仓', studentNo: '2020010202', college: '计算机学院', creditScore: 108, role: 'STUDENT', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000002', createdAt: '2026-09-06 09:10:00' },
  { id: 4, nickname: '三食堂店员', studentNo: '—', college: '后勤服务中心', creditScore: 100, role: 'CLERK', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000003', createdAt: '2026-09-06 09:30:00' },
  { id: 5, nickname: '班委小张', studentNo: '2023010303', college: '计算机学院', creditScore: 105, role: 'LEADER', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000004', createdAt: '2026-09-07 13:40:00' },
  { id: 6, nickname: '李小雨', studentNo: '2023020204', college: '外国语学院', creditScore: 98, role: 'STUDENT', verifyState: 'PENDING', status: 'NORMAL', phone: '13800000005', createdAt: '2026-10-12 15:00:00' },
  { id: 7, nickname: '王浩然', studentNo: '2022030305', college: '机械工程学院', creditScore: 75, role: 'STUDENT', verifyState: 'VERIFIED', status: 'FROZEN', phone: '13800000006', createdAt: '2026-10-18 11:25:00' },
  { id: 8, nickname: '陈思远', studentNo: '2021010406', college: '数学学院', creditScore: 110, role: 'STUDENT', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000007', createdAt: '2026-09-20 16:45:00' },
  { id: 9, nickname: '赵一鸣', studentNo: '2023040507', college: '经济管理学院', creditScore: 92, role: 'STUDENT', verifyState: 'UNVERIFIED', status: 'NORMAL', phone: '13800000008', createdAt: '2026-11-02 19:30:00' },
  { id: 10, nickname: '图书角店长', studentNo: '—', college: '图书馆', creditScore: 100, role: 'CLERK', verifyState: 'VERIFIED', status: 'NORMAL', phone: '13800000009', createdAt: '2026-10-01 08:00:00' }
]

const tickets = [
  { id: 1, ticketNo: 'TK20261127001', type: 'REFUND', orderNo: 'TB20261121101507', status: 'PENDING', initiator: '小黄同学', createdAt: '2026-11-27 09:12:00', title: '卖家一直不发货，申请退款', result: '' },
  { id: 2, ticketNo: 'TK20261126002', type: 'FAKE_BOOK', orderNo: 'TB20261125102003', status: 'PROCESSING', initiator: '陈思远', createdAt: '2026-11-26 20:05:00', title: '收到的书疑似盗版，印刷模糊', result: '' },
  { id: 3, ticketNo: 'TK20261125003', type: 'CONDITION', orderNo: 'TB20261124183004', status: 'PENDING', initiator: '小黄同学', createdAt: '2026-11-25 18:40:00', title: '实际品相与描述不符，缺页', result: '' },
  { id: 4, ticketNo: 'TK20261124004', type: 'OTHER', orderNo: 'TB20261123110005', status: 'RESOLVED', initiator: '赵一鸣', createdAt: '2026-11-24 11:22:00', title: '自提点联系不上，请求协助', result: '已协调自提点店员，买家已取书' },
  { id: 5, ticketNo: 'TK20261123005', type: 'REFUND', orderNo: 'TB20261122140006', status: 'REJECTED', initiator: '李小雨', createdAt: '2026-11-23 15:10:00', title: '无理由退款', result: '书籍已核销交付，不符合退款条件' },
  { id: 6, ticketNo: 'TK20261122006', type: 'FAKE_BOOK', orderNo: 'TB20261121101507', status: 'PENDING', initiator: '王浩然', createdAt: '2026-11-22 10:35:00', title: '封面与 ISBN 不一致', result: '' },
  { id: 7, ticketNo: 'TK20261121007', type: 'CONDITION', orderNo: 'TB20261120160008', status: 'RESOLVED', initiator: '班委小张', createdAt: '2026-11-21 09:00:00', title: '书籍有大量水渍', result: '卖家已补偿 5 元，双方达成一致' },
  { id: 8, ticketNo: 'TK20261120008', type: 'OTHER', orderNo: 'TB20261119120009', status: 'PROCESSING', initiator: '陈思远', createdAt: '2026-11-20 17:45:00', title: '核销码无法展示', result: '' }
]

const groupBuys = [
  { id: 1, title: '高数A（上）第七版 · 三班教材接龙', leader: '班委小张', targetCount: 40, currentCount: 36, unitPrice: 15.0, deadline: '2026-12-15 23:59:59', status: 'OPEN', inviteCode: 'MATH7A' },
  { id: 2, title: '线代第五版 · 软件 2301 班', leader: '班委小张', targetCount: 35, currentCount: 35, unitPrice: 10.0, deadline: '2026-11-30 23:59:59', status: 'CLOSED', inviteCode: 'LINEAR5' },
  { id: 3, title: '大学英语 2 · 外国语学院团购', leader: '李小雨', targetCount: 50, currentCount: 48, unitPrice: 12.0, deadline: '2026-12-08 23:59:59', status: 'OPEN', inviteCode: 'ENG2026' },
  { id: 4, title: '数据结构（C语言版）· 计科 2202', leader: '陈思远', targetCount: 30, currentCount: 30, unitPrice: 20.0, deadline: '2026-11-20 23:59:59', status: 'DONE', inviteCode: 'DS2202' },
  { id: 5, title: '数据库系统概论 · 第五班', leader: '小黄同学', targetCount: 25, currentCount: 18, unitPrice: 14.0, deadline: '2026-12-20 23:59:59', status: 'OPEN', inviteCode: 'DB2026' },
  { id: 6, title: '计算机网络第七版 · 信安方向', leader: '王浩然', targetCount: 20, currentCount: 6, unitPrice: 18.0, deadline: '2026-12-02 23:59:59', status: 'CANCELED', inviteCode: 'NET7TH' },
  { id: 7, title: '概率论第四版 · 数学学院', leader: '陈思远', targetCount: 45, currentCount: 41, unitPrice: 9.9, deadline: '2026-12-12 23:59:59', status: 'OPEN', inviteCode: 'PROB4TH' },
  { id: 8, title: '大学物理上册 · 机械 2303', leader: '赵一鸣', targetCount: 38, currentCount: 38, unitPrice: 13.0, deadline: '2026-11-28 23:59:59', status: 'DONE', inviteCode: 'PHY2303' }
]

function refundOfOrder(order) {
  if (!order) return null
  return refunds.find((r) => r.orderId === order.id) || null
}

// 订单列表/详情统一带出退款信息，供纠纷仲裁页和订单抽屉使用
function orderView(order) {
  const refund = refundOfOrder(order)
  return {
    ...order,
    refund: refund || null,
    refundId: refund ? refund.id : null,
    refundStatus: refund ? refund.status : '',
    refundReason: order.refundReason || (refund ? refund.reason : '')
  }
}

function ok(data) {
  return { code: 0, message: 'ok', data }
}

function fail(message) {
  return { code: 400, message, data: null }
}

function paginate(list, page, size) {
  const p = Math.max(1, Number(page) || 1)
  const s = Math.max(1, Number(size) || 10)
  const start = (p - 1) * s
  return { records: list.slice(start, start + s), total: list.length, page: p, size: s }
}

function parseBody(config) {
  const raw = config.data
  if (!raw) return {}
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch (e) {
      return {}
    }
  }
  return raw
}

function normalizeUrl(config) {
  let url = config.url || ''
  const base = config.baseURL || ''
  if (base && !/^https?:\/\//i.test(url)) {
    url = `${base.replace(/\/$/, '')}/${url.replace(/^\//, '')}`
  }
  url = url.split('?')[0].replace(/^https?:\/\/[^/]+/i, '')
  const i = url.indexOf('/v1/')
  if (i > 0) url = url.slice(i)
  return url
}

function route(method, config) {
  const url = normalizeUrl(config)
  const params = config.params || {}
  const body = parseBody(config)

  if (method === 'POST' && url === '/v1/auth/login') {
    if (body.account === 'admin' && body.password === 'admin123') {
      return ok({ token: TOKEN, user: ADMIN })
    }
    return fail('账号或密码错误（演示账号 admin / admin123）')
  }

  if (method === 'GET' && url === '/v1/auth/me') {
    return ok(ADMIN)
  }

  if (method === 'GET' && url === '/v1/admin/dashboard') {
    const days = []
    const gmvSeed = [268.0, 312.5, 205.0, 438.0, 386.5, 462.0, 386.0]
    const orderSeed = [12, 15, 9, 21, 18, 24, 18]
    for (let i = 6; i >= 0; i -= 1) {
      const d = dayjs().subtract(i, 'day')
      days.push({
        date: d.format('YYYY-MM-DD'),
        gmv: gmvSeed[6 - i],
        orderCount: orderSeed[6 - i]
      })
    }
    return ok({
      today: { gmv: 386.0, orderCount: 18, verifyCount: 12, newUserCount: 9 },
      trend: days,
      funnel: { publish: 1326, view: 8462, order: 512, pay: 438, verify: 376 }
    })
  }

  if (method === 'GET' && url === '/v1/admin/textbooks') {
    const keyword = String(params.keyword || '').trim()
    let list = textbooks.slice()
    if (keyword) {
      list = list.filter(
        (t) => t.title.includes(keyword) || t.isbn13.includes(keyword) || t.publisher.includes(keyword) || t.author.includes(keyword)
      )
    }
    return ok(paginate(list, params.page, params.size))
  }

  if (method === 'POST' && url === '/v1/admin/textbooks') {
    const nextId = textbooks.reduce((m, t) => Math.max(m, t.id), 0) + 1
    const record = {
      id: nextId,
      isbn13: body.isbn13 || '',
      title: body.title || '未命名教材',
      author: body.author || '',
      publisher: body.publisher || '',
      edition: body.edition || '',
      publishYear: body.publishYear || 0,
      price: Number(body.price) || 0,
      category: body.category || '公共课',
      coverUrl: cover((body.title || '新书').slice(0, 3), nextId),
      status: body.status || 'ENABLED'
    }
    textbooks.unshift(record)
    courseMap[nextId] = []
    return ok(record)
  }

  const courseMatch = url.match(/^\/v1\/admin\/textbooks\/(\d+)\/courses$/)
  if (method === 'GET' && courseMatch) {
    return ok(courseMap[courseMatch[1]] || [])
  }

  if (method === 'GET' && url === '/v1/admin/listings') {
    const status = params.status
    let list = listings.slice()
    if (status) {
      list = list.filter((l) => l.status === status)
    }
    return ok(paginate(list, params.page, params.size))
  }

  const approveMatch = url.match(/^\/v1\/admin\/listings\/(\d+)\/approve$/)
  if (method === 'POST' && approveMatch) {
    const item = listings.find((l) => l.id === Number(approveMatch[1]))
    if (!item) return fail('商品不存在')
    item.status = 'ON_SALE'
    return ok({ id: item.id, status: item.status })
  }

  const rejectMatch = url.match(/^\/v1\/admin\/listings\/(\d+)\/reject$/)
  if (method === 'POST' && rejectMatch) {
    if (!body.reason) return fail('请填写驳回理由')
    const item = listings.find((l) => l.id === Number(rejectMatch[1]))
    if (!item) return fail('商品不存在')
    item.status = 'REJECTED'
    item.rejectReason = body.reason
    return ok({ id: item.id, status: item.status })
  }

  if (method === 'GET' && url === '/v1/admin/merchants') {
    const status = String(params.status || '').toUpperCase()
    let list = merchants.slice().sort((a, b) => b.id - a.id)
    if (status) {
      list = list.filter((m) => m.status === status)
    }
    // 后端返回全量数组，不分页
    return ok(list)
  }

  const merchantReviewMatch = url.match(/^\/v1\/admin\/merchants\/(\d+)\/review$/)
  if (method === 'POST' && merchantReviewMatch) {
    const item = merchants.find((m) => m.id === Number(merchantReviewMatch[1]))
    if (!item) return fail('商户申请不存在')
    if (item.status !== 'PENDING') return fail('该商户不在待审核状态')
    const approved = body.approved === true || body.approved === 'true'
    if (!approved && !String(body.reason || '').trim()) return fail('请填写驳回理由')
    item.status = approved ? 'APPROVED' : 'REJECTED'
    item.approvedBy = ADMIN.id
    item.approvedAt = dayjs().format('YYYY-MM-DD HH:mm:ss')
    item.rejectReason = approved ? '' : String(body.reason || '').trim()
    const user = users.find((u) => u.id === item.userId)
    if (approved && user) {
      user.role = 'MERCHANT'
    }
    return ok(item)
  }

  if (method === 'GET' && url === '/v1/admin/orders') {
    const status = params.status
    let list = orders.slice()
    if (status) {
      list = list.filter((o) => o.status === status)
    }
    return ok(paginate(list.map(orderView), params.page, params.size))
  }

  const orderDetailMatch = url.match(/^\/v1\/orders\/([^/]+)$/)
  if (method === 'GET' && orderDetailMatch) {
    const orderNo = decodeURIComponent(orderDetailMatch[1])
    const order = orders.find((o) => o.orderNo === orderNo)
    if (!order) return fail('订单不存在')
    return ok(orderView(order))
  }

  const judgeMatch = url.match(/^\/v1\/orders\/refunds\/(\d+)\/judge$/)
  if (method === 'POST' && judgeMatch) {
    const refund = refunds.find((r) => r.id === Number(judgeMatch[1]))
    if (!refund) return fail('退款单不存在')
    if (refund.status !== 'ARBITRATING') return fail(`该退货不在仲裁中：${refund.status}`)
    const buyerWin = body.buyerWin === true || body.buyerWin === 'true'
    const note = String(body.note || '').trim()
    if (!note) return fail('请填写判定说明')
    const now = dayjs().format('YYYY-MM-DD HH:mm:ss')
    refund.status = 'ARBITRATED'
    refund.platformVerdict = buyerWin ? 'SELLER_FAULT' : 'PLATFORM_OK'
    refund.platformRemark = note
    refund.decidedBy = ADMIN.id
    refund.decidedAt = now
    const order = orders.find((o) => o.id === refund.orderId)
    if (!order) return fail('退款单关联订单不存在')
    if (buyerWin) {
      refund.refundedAt = now
      order.status = 'REFUNDED'
      order.refundReason = refund.reason
      order.completedAt = ''
      const listing = listings.find((l) => l.title === order.title && l.status === 'SOLD')
      if (listing) listing.status = 'ON_SALE'
    } else {
      order.status = 'COMPLETED'
      order.completedAt = now
    }
    order.timeline = timelineOf(order.createdAt, order.status)
    return ok({ refund, order: orderView(order) })
  }

  if (method === 'GET' && url === '/v1/admin/users') {
    const keyword = String(params.keyword || '').trim()
    let list = users.slice()
    if (keyword) {
      list = list.filter((u) => u.nickname.includes(keyword) || u.studentNo.includes(keyword) || u.college.includes(keyword))
    }
    return ok(paginate(list, params.page, params.size))
  }

  const freezeMatch = url.match(/^\/v1\/admin\/users\/(\d+)\/freeze$/)
  if (method === 'POST' && freezeMatch) {
    const item = users.find((u) => u.id === Number(freezeMatch[1]))
    if (!item) return fail('用户不存在')
    item.status = body.frozen ? 'FROZEN' : 'NORMAL'
    return ok({ id: item.id, status: item.status })
  }

  if (method === 'GET' && url === '/v1/admin/tickets') {
    const status = params.status
    let list = tickets.slice()
    if (status) {
      list = list.filter((t) => t.status === status)
    }
    return ok(paginate(list, params.page, params.size))
  }

  const resolveMatch = url.match(/^\/v1\/admin\/tickets\/(\d+)\/resolve$/)
  if (method === 'POST' && resolveMatch) {
    const item = tickets.find((t) => t.id === Number(resolveMatch[1]))
    if (!item) return fail('工单不存在')
    item.status = body.status || 'RESOLVED'
    item.result = body.result || ''
    item.closedAt = dayjs().format('YYYY-MM-DD HH:mm:ss')
    return ok({ id: item.id, status: item.status })
  }

  if (method === 'GET' && url === '/v1/admin/group-buys') {
    return ok(paginate(groupBuys.slice(), params.page, params.size))
  }

  return fail(`Mock 未实现的接口：${method} ${url}`)
}

const mockAdapter = (config) =>
  new Promise((resolve) => {
    const method = (config.method || 'get').toUpperCase()
    let payload
    try {
      payload = route(method, config)
    } catch (e) {
      payload = fail(e.message || 'Mock 处理异常')
    }
    setTimeout(() => {
      resolve({
        data: payload,
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
        request: {}
      })
    }, 120)
  })

export function setupMock(axiosInstance) {
  if (import.meta.env.VITE_USE_MOCK !== 'true') {
    return false
  }
  axiosInstance.interceptors.request.use((config) => {
    config.adapter = mockAdapter
    return config
  })
  return true
}
