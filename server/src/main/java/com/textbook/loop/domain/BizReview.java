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
 * biz_review
 */
@Data
@TableName("biz_review")
public class BizReview implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long reviewerId;

    /** 被评价人 */
    private Long targetId;

    /** BUYER/SELLER */
    private String reviewerRole;

    /** 1-5 星 */
    private Integer rating;

    private String content;

    /** 逗号分隔 */
    private String images;

    private Integer anonymous;

    /** VISIBLE/HIDDEN */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
