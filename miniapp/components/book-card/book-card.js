/**
 * 商品卡片：封面 / 书名 / 价格 / 品相 / 宿舍 / 距离
 * 用法：<book-card item="{{item}}" show-distance="{{true}}"></book-card>
 * 点击后由组件自身跳转详情页
 */
const fmt = require('../../utils/format')

Component({
  options: {
    styleIsolation: 'isolated'
  },

  properties: {
    item: {
      type: Object,
      value: {}
    },
    // 是否展示距离（同校推荐展示，我的书架不展示）
    showDistance: {
      type: Boolean,
      value: true
    }
  },

  data: {
    tone: 0,
    coverTitle: '',
    priceText: '0.00',
    originText: '',
    conditionText: '',
    distanceText: ''
  },

  observers: {
    item: function (value) {
      if (!value || !value.id) return
      const title = value.title || ''
      this.setData({
        tone: Math.floor(Number(value.id) % 5),
        coverTitle: title.length > 14 ? title.slice(0, 14) : title,
        priceText: fmt.formatPrice(value.price),
        originText: value.originalPrice ? fmt.formatPrice(value.originalPrice) : '',
        conditionText: fmt.conditionText(value.conditionLevel),
        distanceText:
          value.distanceKm === null || value.distanceKm === undefined
            ? ''
            : fmt.distanceText(value.distanceKm)
      })
    }
  },

  methods: {
    onTap: function () {
      const item = this.data.item
      if (!item || !item.id) return
      wx.navigateTo({ url: '/pages/listing/detail?id=' + item.id })
    }
  }
})
