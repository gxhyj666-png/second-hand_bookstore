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
 * biz_track_event
 */
@Data
@TableName("biz_track_event")
public class BizTrackEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 一次访问会话 */
    private String sessionId;

    /** VIEW_LISTING/DETAIL/PUBLISH/CREATE_ORDER/PAY/VERIFY */
    private String event;

    private String refType;

    private Long refId;

    private String page;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
