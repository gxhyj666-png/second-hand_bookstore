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
 * biz_message
 */
@Data
@TableName("biz_message")
public class BizMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long senderId;

    /** TEXT/MEET_CARD/REFUND_CARD/SIGN_CARD */
    private String msgType;

    private String content;

    /** 关联的 meet/refund 主键 */
    private Long refId;

    private Integer readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
