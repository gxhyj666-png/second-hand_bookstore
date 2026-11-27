package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * biz_pickup_verify
 */
@Data
@TableName("biz_pickup_verify")
public class BizPickupVerify implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long pointId;

    /** 核销店员 */
    private Long operatorId;

    /** 核销码 */
    private String code;

    /** SCAN/MANUAL */
    private String method;

    private String clientIp;

    private LocalDateTime verifiedAt;
}
