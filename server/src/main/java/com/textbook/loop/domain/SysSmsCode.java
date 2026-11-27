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
 * sys_sms_code
 */
@Data
@TableName("sys_sms_code")
public class SysSmsCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String code;

    /** LOGIN/TRADE */
    private String purpose;

    private LocalDateTime expireAt;

    private Integer used;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
