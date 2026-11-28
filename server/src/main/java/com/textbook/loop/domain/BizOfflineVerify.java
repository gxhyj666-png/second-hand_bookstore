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
 * biz_offline_verify 线下认证与核实：用户提交材料，运营线下核验后置结果。
 */
@Data
@TableName("biz_offline_verify")
public class BizOfflineVerify implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** ID_CARD/STUDENT/MERCHANT */
    private String verifyType;

    /** 材料照片，逗号分隔 */
    private String materialUrl;

    private String applicantNote;

    /** PENDING/PASSED/REJECTED */
    private String status;

    private Long operatorId;

    private String remark;

    private LocalDateTime verifiedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
