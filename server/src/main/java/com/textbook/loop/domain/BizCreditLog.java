package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * biz_credit_log
 */
@Data
@TableName("biz_credit_log")
public class BizCreditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 变动值，可为负 */
    private Integer changeVal;

    /** 变动后分数 */
    private Integer scoreAfter;

    /** DEAL_DONE/REVIEW_GOOD/TIMEOUT/FAKE_BOOK/DISPUTE_LOST */
    private String reason;

    private String refType;

    private Long refId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
