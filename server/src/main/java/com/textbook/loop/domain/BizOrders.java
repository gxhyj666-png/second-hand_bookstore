package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * biz_orders
 */
@Data
@TableName("biz_orders")
public class BizOrders implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号  TB+yyyyMMddHHmmss+6位随机 */
    private String orderNo;

    private Long buyerId;

    private Long sellerId;

    private Long listingId;

    private Long textbookId;

    /** 自提点 */
    private Long pointId;

    /** 商品金额 */
    private BigDecimal amount;

    /** 平台服务费 */
    private BigDecimal commission;

    /** PENDING_PAY/FROZEN/WAIT_VERIFY/VERIFIED/SETTLED/REFUNDED/CLOSED */
    private String status;

    /** 核销码（HMAC 生成，30s 轮换） */
    private String verifyCode;

    /** WECHAT/MOCK */
    private String payChannel;

    /** 第三方支付流水号 */
    private String tradeNo;

    private String remark;

    private String refundReason;

    /** 支付超时时间（15 分钟） */
    private LocalDateTime expireAt;

    private LocalDateTime paidAt;

    private LocalDateTime verifiedAt;

    private LocalDateTime settledAt;

    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
