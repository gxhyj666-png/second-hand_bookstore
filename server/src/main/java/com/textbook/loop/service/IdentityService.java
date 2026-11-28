package com.textbook.loop.service;

import com.textbook.loop.common.BizException;
import com.textbook.loop.config.AppProps;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.domain.SysVerifyRecord;
import com.textbook.loop.mapper.SysUserMapper;
import com.textbook.loop.mapper.SysVerifyRecordMapper;
import com.textbook.loop.realname.RealNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实名认证 / 学生认证 / 人脸核身。
 *
 * <p>三方能力（阿里云实人认证、身份证 OCR、人脸比对）在 app.mock-identity=true 时走本地模拟，
 * 只做格式校验并直接通过；接入真实服务时把该开关关掉并实现 provider 调用即可。
 * 交易类操作要求「实名 + 学生」双认证通过（见 {@link #requireTradePermission(Long)}）。</p>
 */
@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);
    private static final String ID_NO_REGEX = "^\\d{17}[\\dXx]$";

    private final SysUserMapper userMapper;
    private final SysVerifyRecordMapper recordMapper;
    private final AppProps props;
    private final RealNameProvider realNameProvider;

    public IdentityService(SysUserMapper userMapper, SysVerifyRecordMapper recordMapper, AppProps props,
                           RealNameProvider realNameProvider) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.props = props;
        this.realNameProvider = realNameProvider;
    }

    /** 实名认证：身份证正反面 + 姓名/证号核验。 */
    @Transactional
    public Map<String, Object> verifyIdCard(Long userId, String realName, String idNo,
                                            String frontUrl, String backUrl) {
        if (realName == null || realName.isBlank()) {
            throw BizException.badRequest("请填写真实姓名");
        }
        if (idNo == null || !idNo.matches(ID_NO_REGEX)) {
            throw BizException.badRequest("身份证号格式不正确（应为 18 位）");
        }
        if (isBlank(frontUrl) || isBlank(backUrl)) {
            throw BizException.badRequest("请上传身份证正反面照片");
        }
        SysUser user = requireUser(userId);
        RealNameProvider.CheckResult check = realNameProvider.check(realName.trim(), idNo);
        record(userId, "ID_CARD", check.passed(), idNo.substring(0, 4) + "****" + idNo.substring(14),
                realNameProvider.name() + "：" + check.message());
        if (!check.passed()) {
            throw BizException.conflict("实名认证未通过：" + check.message());
        }
        user.setRealName(realName.trim());
        user.setIdNo(maskIdNo(idNo));
        user.setIdCardFront(frontUrl);
        user.setIdCardBack(backUrl);
        user.setIdVerified(1);
        userMapper.updateById(user);
        log.info("用户 {} 实名认证通过", userId);
        return status(userId);
    }

    /** 学生认证：学生证封面 + 个人信息页。 */
    @Transactional
    public Map<String, Object> verifyStudent(Long userId, String schoolName, String studentNo,
                                             String coverUrl, String infoUrl) {
        if (isBlank(coverUrl) || isBlank(infoUrl)) {
            throw BizException.badRequest("请上传学生证封面与个人信息页");
        }
        if (isBlank(studentNo)) {
            throw BizException.badRequest("请填写学号");
        }
        SysUser user = requireUser(userId);
        boolean pass = props.isMockIdentity();
        record(userId, "STUDENT", pass, "学号 " + studentNo, pass ? "MOCK 通过" : "待人工审核");
        if (!pass) {
            user.setVerifyState("PENDING");
            userMapper.updateById(user);
            return status(userId);
        }
        user.setStudentNo(studentNo.trim());
        user.setStudentCardCover(coverUrl);
        user.setStudentCardInfo(infoUrl);
        user.setStudentVerified(1);
        user.setVerifyState("VERIFIED");
        userMapper.updateById(user);
        log.info("用户 {} 学生认证通过", userId);
        return status(userId);
    }

    /** 人脸核身（活体检测由三方完成，这里只做调用与留痕）。 */
    @Transactional
    public Map<String, Object> verifyFace(Long userId, String imageUrl) {
        if (isBlank(imageUrl)) {
            throw BizException.badRequest("请上传人脸照片");
        }
        SysUser user = requireUser(userId);
        if (!Integer.valueOf(1).equals(user.getIdVerified())) {
            throw BizException.conflict("请先完成实名认证，再进行人脸核身");
        }
        boolean pass = props.isMockIdentity();
        record(userId, "FACE", pass, "face-image", pass ? "MOCK 通过" : "待接入三方活体检测");
        if (!pass) {
            throw BizException.conflict("人脸核身暂未开通，请联系管理员");
        }
        user.setFaceVerified(1);
        userMapper.updateById(user);
        return status(userId);
    }

    /** 交易前置条件：实名 + 学生双认证。 */
    public void requireTradePermission(Long userId) {
        SysUser user = requireUser(userId);
        if (!Integer.valueOf(1).equals(user.getIdVerified())) {
            throw BizException.forbidden("请先完成实名认证后再进行交易");
        }
        if (!Integer.valueOf(1).equals(user.getStudentVerified())) {
            throw BizException.forbidden("请先完成学生认证后再进行交易");
        }
        if ("FROZEN".equals(user.getStatus())) {
            throw BizException.forbidden("账号已被冻结，请联系管理员");
        }
    }

    public Map<String, Object> status(Long userId) {
        SysUser user = requireUser(userId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("realName", user.getRealName());
        map.put("idNo", user.getIdNo());
        map.put("idVerified", Integer.valueOf(1).equals(user.getIdVerified()));
        map.put("studentVerified", Integer.valueOf(1).equals(user.getStudentVerified()));
        map.put("faceVerified", Integer.valueOf(1).equals(user.getFaceVerified()));
        map.put("verifyState", user.getVerifyState());
        map.put("studentNo", user.getStudentNo());
        map.put("canTrade", Integer.valueOf(1).equals(user.getIdVerified())
                && Integer.valueOf(1).equals(user.getStudentVerified()));
        return map;
    }

    private void record(Long userId, String type, boolean pass, String payload, String result) {
        SysVerifyRecord r = new SysVerifyRecord();
        r.setUserId(userId);
        r.setVerifyType(type);
        r.setStatus(pass ? "PASS" : "REJECT");
        r.setProvider(props.isMockIdentity() ? "MOCK" : "ALIYUN");
        r.setPayload(payload);
        r.setResult(result);
        recordMapper.insert(r);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private static String maskIdNo(String idNo) {
        return idNo.substring(0, 3) + "***********" + idNo.substring(14);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
