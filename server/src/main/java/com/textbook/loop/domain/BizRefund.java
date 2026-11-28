package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * biz_refund
 */
@Data
@TableName("biz_refund")
public class BizRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 申请方（通常买家） */
    private Long applicantId;

    /** RETURN 退货退款 / REFUND_ONLY 仅退款 */
    private String refundType;

    private String reason;

    /** 凭证照片 */
    private String evidenceUrl;

    private BigDecimal amount;

    /** PENDING/AGREED/REJECTED/ARBITRATING/ARBITRATED */
    private String status;

    /** PLATFORM_OK（货物无问题）/SELLER_FAULT */
    private String platformVerdict;

    private String platformRemark;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    private LocalDateTime refundedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
