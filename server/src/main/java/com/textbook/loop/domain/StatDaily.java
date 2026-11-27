package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * stat_daily
 */
@Data
@TableName("stat_daily")
public class StatDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private Long schoolId;

    /** 成交额 */
    private BigDecimal gmv;

    /** 订单数 */
    private Integer orderCount;

    /** 核销数 */
    private Integer verifyCount;

    /** 新上架数 */
    private Integer listingCount;

    private Integer newUserCount;

    private Integer refundCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
