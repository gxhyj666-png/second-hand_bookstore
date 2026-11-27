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
 * sys_user
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 手机号（加密存储可扩展） */
    private String phone;

    /** 真实姓名 */
    private String realName;

    /** 学号 */
    private String studentNo;

    /** 学校 ID */
    private Long schoolId;

    /** 学院 */
    private String college;

    /** 专业 */
    private String major;

    /** 年级 如 2023 */
    private String grade;

    /** 宿舍楼/房间，用于距离排序 */
    private String dorm;

    /** 信用分，默认 100 */
    private Integer creditScore;

    /** STUDENT/LEADER/CLERK/ADMIN */
    private String role;

    /** UNVERIFIED/PENDING/VERIFIED/REJECTED */
    private String verifyState;

    /** NORMAL/FROZEN */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLogin;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
