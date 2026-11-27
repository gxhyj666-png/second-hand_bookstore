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
 * biz_ticket
 */
@Data
@TableName("biz_ticket")
public class BizTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 发起人 */
    private Long initiatorId;

    /** 被投诉人 */
    private Long againstId;

    /** REFUND/FAKE_BOOK/CONDITION/OTHER */
    private String type;

    private String title;

    private String description;

    private String images;

    /** PENDING/PROCESSING/RESOLVED/REJECTED */
    private String status;

    private String result;

    private Long handlerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}
