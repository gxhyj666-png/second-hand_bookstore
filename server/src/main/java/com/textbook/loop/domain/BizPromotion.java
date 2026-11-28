package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * biz_promotion 推广绑定关系：运营（推广码）→ 下线用户，一个用户只归属一个运营。
 */
@Data
@TableName("biz_promotion")
public class BizPromotion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 运营用户 ID */
    private Long promoterId;

    /** 被推广用户 ID */
    private Long inviteeId;

    private String promoCode;

    /** CODE/LINK/QR */
    private String source;

    private LocalDateTime boundAt;
}
