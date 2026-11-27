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
 * biz_settlement
 */
@Data
@TableName("biz_settlement")
public class BizSettlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long sellerId;

    /** 结算给卖家金额 = 订单金额 - 服务费 */
    private BigDecimal amount;

    /** 平台服务费 */
    private BigDecimal commission;

    /** PENDING/DONE/FAILED */
    private String status;

    /** 结算批次 */
    private String batchNo;

    private LocalDateTime settleAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
