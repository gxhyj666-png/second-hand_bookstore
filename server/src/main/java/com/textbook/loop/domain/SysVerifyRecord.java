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
 * sys_verify_record
 */
@Data
@TableName("sys_verify_record")
public class SysVerifyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** ID_CARD/STUDENT/FACE */
    private String verifyType;

    /** PENDING/PASS/REJECT */
    private String status;

    /** ALIYUN/MOCK */
    private String provider;

    /** 请求摘要（脱敏） */
    private String payload;

    /** 三方返回摘要 */
    private String result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
