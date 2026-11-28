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
 * biz_commission
 */
@Data
@TableName("biz_commission")
public class BizCommission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long promoterId;

    private Long orderId;

    private String orderNo;

    private BigDecimal orderAmount;

    private BigDecimal rate;

    private BigDecimal amount;

    /** PENDING 待结算/SETTLED 已结算/CANCELLED 已退款取消 */
    private String status;

    private LocalDateTime settleAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
