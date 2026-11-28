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
 * biz_commission 推广提成明细：下线订单核销完成后按比例计提。
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

    /** 提成比例，如 0.0100 表示 1% */
    private BigDecimal rate;

    private BigDecimal amount;

    /** PENDING/SETTLED/CANCELLED */
    private String status;

    private LocalDateTime settleAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
