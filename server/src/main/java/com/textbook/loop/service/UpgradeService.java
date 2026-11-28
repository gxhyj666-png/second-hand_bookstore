package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.SysUpgradeCode;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.SysUpgradeCodeMapper;
import com.textbook.loop.mapper.SysUserMapper;
import com.textbook.loop.security.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进阶码：平台生成，个体用户凭码升级为商户或运营。
 *
 * <pre>
 *   MERCHANT 商户：进阶后直接入驻（无需再走人工审核），可批量上下架
 *   OPERATOR 运营：进阶后获得专属推广码，可发展下线、查看业绩与提成、处理投诉与线下认证
 * </pre>
 */
@Service
public class UpgradeService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SysUpgradeCodeMapper codeMapper;
    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final UserRoleService roleService;

    public UpgradeService(SysUpgradeCodeMapper codeMapper, SysUserMapper userMapper,
                          JwtUtil jwtUtil, UserRoleService roleService) {
        this.codeMapper = codeMapper;
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.roleService = roleService;
    }

    /** 管理员批量生成进阶码：目前只有 OPERATOR（运营）一种，expireDays<=0 表示永久有效。 */
    @Transactional
    public List<String> generate(Long adminId, String targetRole, int count, int expireDays) {
        String role = targetRole == null ? "" : targetRole.trim().toUpperCase();
        if (!"OPERATOR".equals(role)) {
            throw BizException.badRequest("进阶码只能用于运营身份；商户身份请用「商户登录」注册并提交入驻资料");
        }
        int n = Math.max(1, Math.min(count, 100));
        String batchNo = "UPC" + LocalDateTime.now().format(BATCH_FMT);
        LocalDateTime expireAt = expireDays > 0 ? LocalDateTime.now().plusDays(expireDays) : null;
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SysUpgradeCode c = new SysUpgradeCode();
            c.setCode(uniqueCode());
            c.setTargetRole(role);
            c.setStatus("UNUSED");
            c.setBatchNo(batchNo);
            c.setIssuedBy(adminId);
            c.setUsedBy(0L);
            c.setExpireAt(expireAt);
            c.setRemark("");
            codeMapper.insert(c);
            codes.add(c.getCode());
        }
        return codes;
    }

    public PageResult<SysUpgradeCode> list(String targetRole, String status, long page, long size) {
        LambdaQueryWrapper<SysUpgradeCode> q = new LambdaQueryWrapper<>();
        if (targetRole != null && !targetRole.isBlank()) {
            q.eq(SysUpgradeCode::getTargetRole, targetRole.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            q.eq(SysUpgradeCode::getStatus, status.toUpperCase());
        }
        q.orderByDesc(SysUpgradeCode::getId);
        return PageResult.of(codeMapper.selectPage(Page.of(page, size), q));
    }

    @Transactional
    public SysUpgradeCode revoke(Long codeId) {
        SysUpgradeCode c = codeMapper.selectById(codeId);
        if (c == null) {
            throw BizException.notFound("进阶码不存在");
        }
        if (!"UNUSED".equals(c.getStatus())) {
            throw BizException.conflict("该进阶码已使用或已作废");
        }
        c.setStatus("REVOKED");
        codeMapper.updateById(c);
        return c;
    }

    /**
     * 用户兑换进阶码（目前只有运营码）：在学生或商户账号上<b>叠加</b>运营身份，
     * 主身份与原有功能（如商户工作台）全部保留，并生成专属推广码。
     * 返回新令牌（附加身份变化，旧令牌里没有 OPERATOR，需要替换）。
     * shopName / contact 仅为兼容旧调用保留，运营进阶不再使用。
     */
    @Transactional
    public Map<String, Object> redeem(Long userId, String code, String shopName, String contact) {
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请填写进阶码");
        }
        SysUpgradeCode c = codeMapper.selectOne(new LambdaQueryWrapper<SysUpgradeCode>()
                .eq(SysUpgradeCode::getCode, code.trim().toUpperCase()).last("limit 1"));
        if (c == null) {
            throw BizException.notFound("进阶码不存在");
        }
        if (!"UNUSED".equals(c.getStatus())) {
            throw BizException.conflict("该进阶码已使用或已作废");
        }
        if (c.getExpireAt() != null && c.getExpireAt().isBefore(LocalDateTime.now())) {
            throw BizException.conflict("该进阶码已过期");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw BizException.conflict("管理员无需进阶");
        }
        if (!"OPERATOR".equals(c.getTargetRole())) {
            throw BizException.badRequest("该进阶码不是运营码，无法使用");
        }
        if (roleService.has(user, "OPERATOR")) {
            throw BizException.conflict("当前账号已经是运营身份");
        }

        // 只叠加运营身份，不改主身份：学生还是学生，商户还是商户，原有功能全部保留。
        roleService.grant(userId, "OPERATOR", "UPGRADE");
        if (user.getPromoCode() == null || user.getPromoCode().isBlank()) {
            user.setPromoCode(uniquePromoCode());
            userMapper.updateById(user);
        }

        c.setStatus("USED");
        c.setUsedBy(userId);
        c.setUsedAt(LocalDateTime.now());
        codeMapper.updateById(c);

        java.util.List<String> roles = roleService.rolesOf(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", user.getRole());
        result.put("roles", roles);
        result.put("promoCode", user.getPromoCode());
        result.put("token", jwtUtil.issue(user.getId(), user.getRole(), String.join(",", roles)));
        result.put("user", user);
        return result;
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = random(8);
            Long exists = codeMapper.selectCount(new LambdaQueryWrapper<SysUpgradeCode>()
                    .eq(SysUpgradeCode::getCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw BizException.conflict("进阶码生成失败，请重试");
    }

    private String uniquePromoCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = random(6);
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPromoCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw BizException.conflict("推广码生成失败，请重试");
    }

    private String random(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
