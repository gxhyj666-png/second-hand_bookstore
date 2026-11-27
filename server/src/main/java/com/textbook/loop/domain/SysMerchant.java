package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * sys_merchant
 */
@Data
@TableName("sys_merchant")
public class SysMerchant implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户主账号 */
    private Long userId;

    private String shopName;

    /** 营业执照/校园代理授权 */
    private String licenseUrl;

    private String contact;

    /** PENDING/APPROVED/REJECTED/FROZEN */
    private String status;

    private String rejectReason;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    /** 在架数量（冗余，批量操作后刷新） */
    private Integer listingCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
