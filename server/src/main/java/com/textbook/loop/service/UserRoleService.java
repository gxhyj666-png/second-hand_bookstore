package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.domain.SysUserRole;
import com.textbook.loop.mapper.SysUserMapper;
import com.textbook.loop.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 多身份聚合：一个人只有一个账号，团长 / 商户 / 运营都是同一账号上叠加的身份。
 *
 * <p>主身份存 {@code sys_user.role}（登录时选择的那重），额外获得的身份存
 * {@code sys_user_role}。例如商户用进阶码升级运营后：主身份仍是 MERCHANT，
 * 附加身份多一条 OPERATOR，两套功能都能用。</p>
 */
@Service
public class UserRoleService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper roleMapper;

    public UserRoleService(SysUserMapper userMapper, SysUserRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /** 全部身份：主身份在前，附加身份按授予顺序在后。 */
    public List<String> rolesOf(SysUser user) {
        Set<String> roles = new LinkedHashSet<>();
        if (user != null && user.getRole() != null && !user.getRole().isBlank()) {
            roles.add(user.getRole());
        }
        if (user != null && user.getId() != null) {
            List<SysUserRole> extras = roleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, user.getId())
                    .orderByAsc(SysUserRole::getId));
            for (SysUserRole r : extras) {
                if (r.getRole() != null && !r.getRole().isBlank()) {
                    roles.add(r.getRole());
                }
            }
        }
        return new ArrayList<>(roles);
    }

    public List<String> rolesOf(Long userId) {
        return rolesOf(userId == null ? null : userMapper.selectById(userId));
    }

    /** 逗号串，用于写进 JWT。 */
    public String rolesText(SysUser user) {
        return String.join(",", rolesOf(user));
    }

    /** 是否拥有某个身份（主身份或附加身份）。 */
    public boolean has(SysUser user, String role) {
        return role != null && rolesOf(user).contains(role);
    }

    public boolean has(Long userId, String role) {
        return has(userId == null ? null : userMapper.selectById(userId), role);
    }

    /** 授予附加身份；已存在则忽略（幂等）。 */
    @Transactional
    public void grant(Long userId, String role, String source) {
        if (userId == null || role == null || role.isBlank()) {
            return;
        }
        Long exists = roleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRole, role));
        if (exists != null && exists > 0) {
            return;
        }
        SysUserRole row = new SysUserRole();
        row.setUserId(userId);
        row.setRole(role);
        row.setSource(source == null || source.isBlank() ? "UPGRADE" : source);
        row.setGrantedAt(LocalDateTime.now());
        roleMapper.insert(row);
    }
}
