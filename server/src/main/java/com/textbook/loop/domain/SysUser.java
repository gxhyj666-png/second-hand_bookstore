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

    /** 微信 openid（未绑定微信时为 NULL，唯一索引允许多个 NULL） */
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

    /** 身份证号（对外脱敏） */
    private String idNo;

    /** 实名认证：0 否 1 是 */
    private Integer idVerified;

    /** 人脸核身：0 否 1 是 */
    private Integer faceVerified;

    /** 学生认证：0 否 1 是 */
    private Integer studentVerified;

    /** 身份证人像面图片 */
    private String idCardFront;

    /** 身份证国徽面图片 */
    private String idCardBack;

    /** 学生证封面图片 */
    private String studentCardCover;

    /** 学生证信息页图片 */
    private String studentCardInfo;

    /** 所属商户 ID，0 表示个人卖家 */
    private Long merchantId;

    /** 信用分，默认 100 */
    private Integer creditScore;

    /** STUDENT/LEADER/MERCHANT/OPERATOR/ADMIN */
    private String role;

    /** 运营推广码（仅 OPERATOR 有，唯一，NULL 不占唯一位） */
    private String promoCode;

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
