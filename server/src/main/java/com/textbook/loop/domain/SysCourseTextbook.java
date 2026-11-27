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
 * sys_course_textbook
 */
@Data
@TableName("sys_course_textbook")
public class SysCourseTextbook implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程 ID */
    private Long courseId;

    /** 教材 ID */
    private Long textbookId;

    /** REQUIRED指定/REFERENCE参考 */
    private String bindType;

    /** 授课教师（版本校验第三重） */
    private String teacher;

    /** 适用学期 */
    private String semester;

    /** MANUAL人工/OCR识别/学生补充 */
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
