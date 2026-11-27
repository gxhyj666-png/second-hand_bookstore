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
 * sys_textbook
 */
@Data
@TableName("sys_textbook")
public class SysTextbook implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ISBN-13 */
    private String isbn13;

    /** ISBN-10 */
    private String isbn10;

    /** 书名 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 作者 */
    private String author;

    /** 出版社 */
    private String publisher;

    /** 版次 如 第七版 */
    private String edition;

    /** 出版年 */
    private Integer publishYear;

    /** 定价 */
    private BigDecimal price;

    /** 封面图 */
    private String coverUrl;

    /** 公共课/专业基础/专业核心/通识 */
    private String category;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
