package com.textbook.loop.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * biz_listing
 */
@Data
@TableName("biz_listing")
public class BizListing implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卖家 */
    private Long sellerId;

    /** 教材 ID */
    private Long textbookId;

    /** 关联课程（用于同课匹配） */
    private Long courseId;

    /** 展示标题（书名+版次） */
    private String title;

    /** 售价 */
    private BigDecimal price;

    /** 原价（定价） */
    private BigDecimal originalPrice;

    /** NEW/ LIKE_NEW(9成新)/GOOD(8成新)/FAIR(有笔记)/POOR */
    private String conditionLevel;

    /** 描述 */
    private String description;

    /** 封面（首图） */
    private String coverUrl;

    /** 图片数 */
    private Integer imageCount;

    /** 所在宿舍楼 */
    private String dorm;

    /** ON_SALE/LOCKED/SOLD/OFFLINE/REJECTED */
    private String status;

    private Integer viewCount;

    private Integer favoriteCount;

    private String rejectReason;

    /** 上架时间（排序用） */
    private LocalDateTime publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
