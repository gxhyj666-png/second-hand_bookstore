/**
 * 空状态
 * 用法：<empty text="还没有在售的教材" sub="..." action-text="去卖书" bind:action="goPublish"></empty>
 */
Component({
  options: {
    styleIsolation: 'isolated'
  },

  properties: {
    text: {
      type: String,
      value: '暂无数据'
    },
    sub: {
      type: String,
      value: ''
    },
    actionText: {
      type: String,
      value: ''
    }
  },

  methods: {
    onAction: function () {
      this.triggerEvent('action')
    }
  }
})
