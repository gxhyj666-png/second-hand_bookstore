/**
 * 同窗书 · 校园教材循环平台
 * 全局逻辑：登录态读取、用户信息缓存、全局配置
 */
const auth = require('./utils/auth')
const net = require('./utils/request')

App({
  globalData: {
    // 后端接口根地址（真实模式使用，可在「我的-设置」里临时改）
    baseUrl: net.getBaseUrl(),
    // 当前登录用户；null 表示未登录
    userInfo: null,
    // 单校起步：演示学校固定为示例大学
    schoolId: 1,
    schoolName: '示例大学',
    // 是否正在使用本地假数据
    useMock: net.isMock()
  },

  onLaunch() {
    // 1. 先用本地缓存兜底，避免首屏空白
    const cached = auth.getUser()
    if (cached) {
      this.globalData.userInfo = cached
    }
    // 2. 有 token 时静默刷新一次用户信息；失败不打扰用户
    if (auth.getToken()) {
      this.refreshUser()
    }
  },

  /**
   * 静默拉取当前用户（GET /v1/auth/me）
   * @returns {Promise<Object|null>}
   */
  refreshUser() {
    return auth
      .fetchMe()
      .then((user) => {
        this.globalData.userInfo = user || null
        return user
      })
      .catch(() => {
        // token 失效：清掉本地登录态，页面会按「未登录」状态渲染
        auth.clearLogin()
        this.globalData.userInfo = null
        return null
      })
  },

  /**
   * 登录/刷新后写入全局用户
   * @param {Object} user
   */
  setUserInfo(user) {
    this.globalData.userInfo = user || null
    auth.setUser(user || null)
  },

  /**
   * 退出登录
   */
  logout() {
    auth.clearLogin()
    this.globalData.userInfo = null
  }
})
