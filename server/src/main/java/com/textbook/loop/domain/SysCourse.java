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
 * sys_course
 */
@Data
@TableName("sys_course")
public class SysCourse implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学校 ID */
    private Long schoolId;

    /** 课程代码 */
    private String code;

    /** 课程名 */
    private String name;

    /** 开课学院 */
    private String college;

    /** 适用专业（空=通用） */
    private String major;

    /** 学期 如 2026-2027-1 */
    private String term;

    /** 授课教师 */
    private String teacher;

    /** 学分 */
    private BigDecimal credit;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
