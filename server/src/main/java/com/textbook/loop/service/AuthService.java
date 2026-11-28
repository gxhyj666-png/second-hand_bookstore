package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.common.BizException;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.domain.UserAuth;
import com.textbook.loop.dto.TokenView;
import com.textbook.loop.mapper.SysUserMapper;
import com.textbook.loop.mapper.UserAuthMapper;
import com.textbook.loop.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证与用户资料。
 */
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final UserAuthMapper authMapper;
    private final JwtUtil jwtUtil;
    private final SmsService smsService;
    private final NoticeService noticeService;
    private final UserRoleService roleService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(SysUserMapper userMapper, UserAuthMapper authMapper, JwtUtil jwtUtil,
                       SmsService smsService, NoticeService noticeService, UserRoleService roleService) {
        this.userMapper = userMapper;
        this.authMapper = authMapper;
        this.jwtUtil = jwtUtil;
        this.smsService = smsService;
        this.noticeService = noticeService;
        this.roleService = roleService;
    }

    @Transactional
    public TokenView register(String phone, String password, String nickname) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (password == null || password.length() < 6) {
            throw BizException.badRequest("密码至少 6 位");
        }
        Long exists = authMapper.selectCount(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getAuthType, "PASSWORD")
                .eq(UserAuth::getIdentifier, phone));
        if (exists != null && exists > 0) {
            throw BizException.conflict("该手机号已注册");
        }
        SysUser user = new SysUser();
        user.setNickname(nickname == null || nickname.isBlank() ? "同学" + phone.substring(7) : nickname);
        user.setPhone(phone);
        user.setSchoolId(1L);
        user.setCreditScore(100);
        user.setRole("STUDENT");
        user.setVerifyState("UNVERIFIED");
        user.setStatus("NORMAL");
        user.setLastLogin(LocalDateTime.now());
        userMapper.insert(user);

        UserAuth auth = new UserAuth();
        auth.setUserId(user.getId());
        auth.setAuthType("PASSWORD");
        auth.setIdentifier(phone);
        auth.setCredential(encoder.encode(password));
        auth.setVerified(1);
        authMapper.insert(auth);

        return new TokenView(jwtUtil.issue(user.getId(), user.getRole(), user.getRole()),
                user, roleService.rolesOf(user));
    }

    /**
     * 账号密码登录。{@code loginType} 为 MERCHANT / STUDENT，用于区分登录页上的两个入口；
     * 不传（老脚本、后台调用）则不做身份校验，保持兼容。
     */
    public TokenView login(String account, String password, String loginType) {
        UserAuth auth = authMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getAuthType, "PASSWORD")
                .eq(UserAuth::getIdentifier, account)
                .last("limit 1"));
        if (auth == null || !encoder.matches(password == null ? "" : password, auth.getCredential())) {
            throw BizException.unauthorized("账号或密码错误");
        }
        checkLoginType(auth.getUserId(), loginType);
        return issueFor(auth.getUserId());
    }

    /**
     * 个人中心设置/修改登录密码：短信注册的用户首次设置免旧密码，已有密码的必须校验旧密码。
     */
    @Transactional
    public void setPassword(Long userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw BizException.badRequest("新密码至少 6 位");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        String phone = user.getPhone();
        if (phone == null || phone.isBlank()) {
            throw BizException.conflict("当前账号未绑定手机号，暂不支持密码登录");
        }
        UserAuth auth = authMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getAuthType, "PASSWORD")
                .eq(UserAuth::getIdentifier, phone)
                .last("limit 1"));
        if (auth == null) {
            auth = new UserAuth();
            auth.setUserId(userId);
            auth.setAuthType("PASSWORD");
            auth.setIdentifier(phone);
            auth.setCredential(encoder.encode(newPassword));
            auth.setVerified(1);
            authMapper.insert(auth);
            return;
        }
        if (!encoder.matches(oldPassword == null ? "" : oldPassword, auth.getCredential())) {
            throw BizException.badRequest("原密码不正确");
        }
        auth.setCredential(encoder.encode(newPassword));
        authMapper.updateById(auth);
    }

    /**
     * 微信登录（教学演示版）：code 直接当作 openid，未注册则自动建号。
     */
    @Transactional
    public TokenView wechat(String code) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("缺少微信 code");
        }
        String openid = "wx_" + code.trim();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getOpenid, openid).last("limit 1"));
        if (user == null) {
            user = new SysUser();
            user.setOpenid(openid);
            user.setNickname("微信用户" + Math.abs(openid.hashCode() % 10000));
            user.setSchoolId(1L);
            user.setCreditScore(100);
            user.setRole("STUDENT");
            user.setVerifyState("UNVERIFIED");
            user.setStatus("NORMAL");
            user.setLastLogin(LocalDateTime.now());
            userMapper.insert(user);
        }
        return issueFor(user.getId());
    }


    /**
     * 短信验证码登录：学生与商户共用同一套登录方式，由登录页的「学生登录 / 商户登录」区分入口。
     *
     * <p>手机号未注册时自动建号：商户入口建的是<b>商户账号</b>（role=MERCHANT，尚无店铺，
     * 登录后引导提交入驻资料等运营审核）；学生入口建学生账号。已注册的账号若用错入口，
     * 直接提示切换，不会把学生账号悄悄变成商户。</p>
     */
    @Transactional
    public TokenView smsLogin(String phone, String code, String loginType) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        String type = normalizeLoginType(loginType);
        smsService.verify(phone, "LOGIN", code);
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone).last("limit 1"));
        if (user == null) {
            boolean merchant = "MERCHANT".equals(type);
            user = new SysUser();
            user.setNickname(merchant ? "商户" + phone.substring(7) : "同学" + phone.substring(7));
            user.setPhone(phone);
            user.setSchoolId(1L);
            user.setCreditScore(100);
            user.setRole(merchant ? "MERCHANT" : "STUDENT");
            user.setVerifyState("UNVERIFIED");
            user.setStatus("NORMAL");
            user.setLastLogin(LocalDateTime.now());
            userMapper.insert(user);

            UserAuth auth = new UserAuth();
            auth.setUserId(user.getId());
            auth.setAuthType("SMS");
            auth.setIdentifier(phone);
            auth.setCredential("");
            auth.setVerified(1);
            authMapper.insert(auth);
        } else {
            checkLoginType(user.getId(), type);
        }
        return issueFor(user.getId());
    }

    /** 登录入口校验：商户入口只放商户账号，学生入口不放主身份为商户的账号。 */
    private void checkLoginType(Long userId, String loginType) {
        if (loginType == null || loginType.isBlank()) {
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if ("MERCHANT".equals(loginType)) {
            if (!roleService.has(user, "MERCHANT")) {
                throw BizException.conflict("该手机号是学生账号，不能作为商户登录；如需开店，请用另一个手机号注册商户账号");
            }
        } else if ("STUDENT".equals(loginType)) {
            if ("MERCHANT".equals(user.getRole())) {
                throw BizException.conflict("该账号是商户账号，请切换到「商户登录」");
            }
        }
    }

    private static String normalizeLoginType(String loginType) {
        if (loginType == null || loginType.isBlank()) {
            return "";
        }
        String t = loginType.trim().toUpperCase();
        return "MERCHANT".equals(t) ? "MERCHANT" : "STUDENT";
    }

    private TokenView issueFor(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if ("FROZEN".equals(user.getStatus())) {
            throw BizException.forbidden("账号已被冻结，请联系管理员");
        }
        user.setLastLogin(LocalDateTime.now());
        userMapper.updateById(user);
        java.util.List<String> roles = roleService.rolesOf(user);
        return new TokenView(jwtUtil.issue(user.getId(), user.getRole(), String.join(",", roles)), user, roles);
    }

    public SysUser me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    /** 当前账号的全部身份（主身份在前）。 */
    public java.util.List<String> rolesOf(Long userId) {
        return roleService.rolesOf(userId);
    }

    public SysUser updateProfile(Long userId, String nickname, String avatar, String college,
                                 String major, String grade, String dorm) {
        SysUser user = me(userId);
        if (nickname != null) user.setNickname(nickname);
        if (avatar != null) user.setAvatar(avatar);
        if (college != null) user.setCollege(college);
        if (major != null) user.setMajor(major);
        if (grade != null) user.setGrade(grade);
        if (dorm != null) user.setDorm(dorm);
        userMapper.updateById(user);
        return user;
    }

    /**
     * 换绑手机号：校验新号码的短信验证码（purpose=BIND_PHONE）后同时更新 sys_user.phone
     * 与 user_auth 里 SMS 凭据的 identifier，保证下次能用新号码登录。
     */
    @Transactional
    public SysUser changePhone(Long userId, String phone, String code) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw BizException.badRequest("手机号格式不正确");
        }
        SysUser user = me(userId);
        if (phone.equals(user.getPhone())) {
            throw BizException.badRequest("新手机号与当前手机号相同");
        }
        long used = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(SysUser::getId, userId));
        if (used > 0) {
            throw BizException.conflict("该手机号已被其他账号绑定");
        }
        smsService.verify(phone, "BIND_PHONE", code);

        String old = user.getPhone();
        user.setPhone(phone);
        userMapper.updateById(user);

        UserAuth auth = authMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getAuthType, "SMS")
                .last("limit 1"));
        if (auth != null) {
            auth.setIdentifier(phone);
            authMapper.updateById(auth);
        } else {
            UserAuth created = new UserAuth();
            created.setUserId(userId);
            created.setAuthType("SMS");
            created.setIdentifier(phone);
            created.setCredential("");
            created.setVerified(1);
            authMapper.insert(created);
        }
        String maskedOld = old == null || old.length() < 7 ? "" : old.substring(0, 3) + "****" + old.substring(7);
        String maskedNew = phone.substring(0, 3) + "****" + phone.substring(7);
        noticeService.push(userId, "SYSTEM", "手机号已更换",
                "你的登录手机号已从 " + maskedOld + " 更换为 " + maskedNew + "。如不是你本人操作，请立即联系平台。",
                "USER", userId);
        return user;
    }

    /**
     * 学号实名认证：提交后进入 PENDING，由管理员审核。
     */
    public SysUser verifyStudent(Long userId, String studentNo, String realName) {
        if (studentNo == null || studentNo.isBlank() || realName == null || realName.isBlank()) {
            throw BizException.badRequest("学号与姓名均不能为空");
        }
        SysUser user = me(userId);
        if ("VERIFIED".equals(user.getVerifyState())) {
            throw BizException.conflict("已通过认证");
        }
        user.setStudentNo(studentNo.trim());
        user.setRealName(realName.trim());
        user.setVerifyState("PENDING");
        userMapper.updateById(user);
        return user;
    }
}

