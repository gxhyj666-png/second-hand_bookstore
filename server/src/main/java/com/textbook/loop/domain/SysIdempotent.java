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
 * sys_idempotent
 */
@Data
@TableName("sys_idempotent")
public class SysIdempotent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 客户端 requestId 或回调流水号 */
    @TableId(type = IdType.INPUT)
    private String requestId;

    /** CREATE_ORDER/PAY_CALLBACK/VERIFY */
    private String scene;

    /** 首次处理结果摘要 */
    private String result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
