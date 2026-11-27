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

    /** PENDING_PAY/WAIT_VERIFY/COMPLETED/REFUNDING/REFUND_AGREED/REFUNDED/ARBITRATING/CLOSED（旧值 FROZEN/VERIFIED/SETTLED 兼容读取） */
    private String status;

    /** 6 位随机核销码，买家出示、卖家输入 */
    private String verifyCode;

    /** 核销码有效期（生成后 15 分钟） */
    private LocalDateTime verifyExpireAt;

    /** 交易地点（双方确认） */
    private String meetPlace;

    /** 交易时间（双方确认） */
    private LocalDateTime meetTime;

    /** 线下交易签到照片（卖家上传） */
    private String signPhotoUrl;

    /** 签到时间 */
    private LocalDateTime signAt;

    /** 商户订单所属商户 ID，0 为个人 */
    private Long merchantId;

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

    /** 交易完成时间（核销后） */
    private LocalDateTime completedAt;

    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
