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
 * sys_upgrade_code
 */
@Data
@TableName("sys_upgrade_code")
public class SysUpgradeCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 8 位进阶码 */
    private String code;

    /** MERCHANT 商户 / OPERATOR 运营 */
    private String targetRole;

    /** UNUSED/USED/REVOKED */
    private String status;

    private String batchNo;

    /** 生成人（管理员） */
    private Long issuedBy;

    private Long usedBy;

    private LocalDateTime usedAt;

    private LocalDateTime expireAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
