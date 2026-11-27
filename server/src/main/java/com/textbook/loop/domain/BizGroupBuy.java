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
 * biz_group_buy
 */
@Data
@TableName("biz_group_buy")
public class BizGroupBuy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long textbookId;

    /** 团长（班委） */
    private Long leaderId;

    /** 如 高数A 第七版 教材接龙 */
    private String title;

    /** 目标册数 */
    private Integer targetCount;

    /** 预期单价 */
    private BigDecimal unitPrice;

    /** 邀请码/短码 */
    private String inviteCode;

    /** 已登记数量 */
    private Integer currentCount;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** OPEN/CLOSED/DONE/CANCELED */
    private String status;

    /** 团长佣金比例 */
    private BigDecimal commissionRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
