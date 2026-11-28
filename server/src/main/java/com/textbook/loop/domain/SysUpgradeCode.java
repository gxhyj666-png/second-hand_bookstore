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
 * sys_upgrade_code 商户/运营进阶码：平台生成，一码一用。
 */
@Data
@TableName("sys_upgrade_code")
public class SysUpgradeCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 8 位进阶码 */
    private String code;

    /** MERCHANT / OPERATOR */
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
