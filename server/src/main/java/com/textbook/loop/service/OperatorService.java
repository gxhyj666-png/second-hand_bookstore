package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizCommission;
import com.textbook.loop.domain.BizOfflineVerify;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizPromotion;
import com.textbook.loop.domain.BizTicket;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizCommissionMapper;
import com.textbook.loop.mapper.BizOfflineVerifyMapper;
import com.textbook.loop.mapper.BizOrdersMapper;
import com.textbook.loop.mapper.BizPromotionMapper;
import com.textbook.loop.mapper.BizTicketMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端：推广二维码/推广码、下线交易数据、提成明细、投诉处理、线下认证核实。
 */
@Service
public class OperatorService {

    private final BizPromotionMapper promotionMapper;
    private final BizCommissionMapper commissionMapper;
    private final BizOrdersMapper ordersMapper;
    private final BizTicketMapper ticketMapper;
    private final BizOfflineVerifyMapper offlineVerifyMapper;
    private final SysUserMapper userMapper;
    private final NoticeService noticeService;

    public OperatorService(BizPromotionMapper promotionMapper, BizCommissionMapper commissionMapper,
                           BizOrdersMapper ordersMapper, BizTicketMapper ticketMapper,
                           BizOfflineVerifyMapper offlineVerifyMapper, SysUserMapper userMapper,
                           NoticeService noticeService) {
        this.promotionMapper = promotionMapper;
        this.commissionMapper = commissionMapper;
        this.ordersMapper = ordersMapper;
        this.ticketMapper = ticketMapper;
        this.offlineVerifyMapper = offlineVerifyMapper;
        this.userMapper = userMapper;
        this.noticeService = noticeService;
    }

    // ------------------------------------------------------------------ 概览 / 推广

    public Map<String, Object> overview(Long promoterId) {
        SysUser promoter = userMapper.selectById(promoterId);
        if (promoter == null) {
            throw BizException.notFound("用户不存在");
        }
        List<BizPromotion> promos = promotionMapper.selectList(new LambdaQueryWrapper<BizPromotion>()
                .eq(BizPromotion::getPromoterId, promoterId));
        List<Long> inviteeIds = promos.stream().map(BizPromotion::getInviteeId).toList();

        int orderCount = 0;
        BigDecimal gmv = BigDecimal.ZERO;
        if (!inviteeIds.isEmpty()) {
            List<BizOrders> orders = ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                    .in(BizOrders::getBuyerId, inviteeIds)
                    .eq(BizOrders::getStatus, "COMPLETED"));
            orderCount = orders.size();
            gmv = orders.stream().map(BizOrders::getAmount)
                    .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        List<BizCommission> commissions = commissionMapper.selectList(new LambdaQueryWrapper<BizCommission>()
                .eq(BizCommission::getPromoterId, promoterId));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        for (BizCommission c : commissions) {
            if ("CANCELLED".equals(c.getStatus())) {
                continue;
            }
            total = total.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            if ("PENDING".equals(c.getStatus())) {
                pending = pending.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            } else if ("SETTLED".equals(c.getStatus())) {
                settled = settled.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            }
        }

        Long complaintPending = ticketMapper.selectCount(new LambdaQueryWrapper<BizTicket>()
                .ne(BizTicket::getAgainstId, 0L)
                .in(BizTicket::getStatus, List.of("PENDING", "PROCESSING")));
        Long offlinePending = offlineVerifyMapper.selectCount(new LambdaQueryWrapper<BizOfflineVerify>()
                .eq(BizOfflineVerify::getStatus, "PENDING"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("promoCode", promoter.getPromoCode());
        map.put("inviteCode", promoter.getPromoCode());
        map.put("promoLink", "/#/register?promo=" + (promoter.getPromoCode() == null ? "" : promoter.getPromoCode()));
        map.put("inviteeCount", inviteeIds.size());
        map.put("orderCount", orderCount);
        map.put("gmv", gmv);
        map.put("commissionTotal", total);
        map.put("commissionPending", pending);
        map.put("commissionSettled", settled);
        map.put("complaintPending", complaintPending);
        map.put("offlinePending", offlinePending);
        return map;
    }

    /** 用户绑定运营推广码（一个用户只能绑定一次，不能绑自己）。 */
    @Transactional
    public Map<String, Object> bind(Long inviteeId, String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            throw BizException.badRequest("请填写推广码");
        }
        String code = promoCode.trim().toUpperCase();
        SysUser promoter = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPromoCode, code).last("limit 1"));
        if (promoter == null || !"OPERATOR".equals(promoter.getRole())) {
            throw BizException.notFound("推广码无效");
        }
        if (promoter.getId().equals(inviteeId)) {
            throw BizException.conflict("不能绑定自己的推广码");
        }
        Long exists = promotionMapper.selectCount(new LambdaQueryWrapper<BizPromotion>()
                .eq(BizPromotion::getInviteeId, inviteeId));
        if (exists != null && exists > 0) {
            throw BizException.conflict("你已绑定过推广关系");
        }
        BizPromotion p = new BizPromotion();
        p.setPromoterId(promoter.getId());
        p.setInviteeId(inviteeId);
        p.setPromoCode(code);
        p.setSource("CODE");
        p.setBoundAt(LocalDateTime.now());
        promotionMapper.insert(p);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("promoterId", promoter.getId());
        map.put("promoterNickname", promoter.getNickname());
        map.put("boundAt", p.getBoundAt());
        return map;
    }

    /** 下线列表：昵称、学校、注册时间、成交单数与成交额。 */
    public PageResult<Map<String, Object>> invitees(Long promoterId, long page, long size) {
        Page<BizPromotion> p = promotionMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<BizPromotion>()
                        .eq(BizPromotion::getPromoterId, promoterId)
                        .orderByDesc(BizPromotion::getBoundAt));
        List<Long> ids = p.getRecords().stream().map(BizPromotion::getInviteeId).toList();
        Map<Long, SysUser> users = new HashMap<>();
        Map<Long, int[]> orderStats = new HashMap<>();
        Map<Long, BigDecimal> gmv = new HashMap<>();
        if (!ids.isEmpty()) {
            userMapper.selectBatchIds(ids).forEach(u -> users.put(u.getId(), u));
            List<BizOrders> orders = ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                    .in(BizOrders::getBuyerId, ids)
                    .eq(BizOrders::getStatus, "COMPLETED"));
            for (BizOrders o : orders) {
                orderStats.computeIfAbsent(o.getBuyerId(), k -> new int[1])[0]++;
                gmv.merge(o.getBuyerId(), o.getAmount() == null ? BigDecimal.ZERO : o.getAmount(), BigDecimal::add);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizPromotion promo : p.getRecords()) {
            SysUser u = users.get(promo.getInviteeId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", promo.getInviteeId());
            row.put("nickname", u == null ? "已注销用户" : u.getNickname());
            row.put("phone", u == null ? "" : maskPhone(u.getPhone()));
            row.put("college", u == null ? "" : u.getCollege());
            row.put("creditScore", u == null ? null : u.getCreditScore());
            row.put("boundAt", promo.getBoundAt());
            row.put("orderCount", orderStats.getOrDefault(promo.getInviteeId(), new int[1])[0]);
            row.put("gmv", gmv.getOrDefault(promo.getInviteeId(), BigDecimal.ZERO));
            rows.add(row);
        }
        return new PageResult<>(rows, p.getTotal(), p.getCurrent(), p.getSize());
    }

    public PageResult<BizCommission> commissions(Long promoterId, String status, long page, long size) {
        LambdaQueryWrapper<BizCommission> q = new LambdaQueryWrapper<BizCommission>()
                .eq(BizCommission::getPromoterId, promoterId);
        if (status != null && !status.isBlank()) {
            q.eq(BizCommission::getStatus, status.toUpperCase());
        }
        q.orderByDesc(BizCommission::getId);
        return PageResult.of(commissionMapper.selectPage(Page.of(page, size), q));
    }

    // ------------------------------------------------------------------ 投诉 / 线下认证

    public PageResult<BizTicket> complaints(String status, long page, long size) {
        LambdaQueryWrapper<BizTicket> q = new LambdaQueryWrapper<BizTicket>()
                .ne(BizTicket::getAgainstId, 0L);
        if (status != null && !status.isBlank()) {
            q.eq(BizTicket::getStatus, status.toUpperCase());
        } else {
            q.in(BizTicket::getStatus, List.of("PENDING", "PROCESSING"));
        }
        q.orderByDesc(BizTicket::getId);
        return PageResult.of(ticketMapper.selectPage(Page.of(page, size), q));
    }

    @Transactional
    public BizTicket handleComplaint(Long operatorId, Long ticketId, String status, String result) {
        BizTicket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw BizException.notFound("投诉不存在");
        }
        String s = status == null ? "RESOLVED" : status.toUpperCase();
        if (!List.of("PROCESSING", "RESOLVED", "REJECTED").contains(s)) {
            throw BizException.badRequest("处理结果只能是 PROCESSING/RESOLVED/REJECTED");
        }
        t.setStatus(s);
        t.setResult(result == null ? "" : result);
        t.setHandlerId(operatorId);
        if (!"PROCESSING".equals(s)) {
            t.setClosedAt(LocalDateTime.now());
        }
        ticketMapper.updateById(t);
        noticeService.push(t.getInitiatorId(), "TICKET", "投诉处理进展",
                "你的投诉「" + t.getTitle() + "」状态已更新为 " + s + "。", "TICKET", t.getId());
        return t;
    }

    /** 用户提交线下认证材料（身份证/学生证），等待运营线下核实。 */
    @Transactional
    public BizOfflineVerify applyOffline(Long userId, String verifyType, String materialUrl, String note) {
        String type = verifyType == null ? "STUDENT" : verifyType.toUpperCase();
        if (!List.of("ID_CARD", "STUDENT", "MERCHANT").contains(type)) {
            throw BizException.badRequest("认证类型不正确");
        }
        if (materialUrl == null || materialUrl.isBlank()) {
            throw BizException.badRequest("请上传认证材料照片");
        }
        BizOfflineVerify v = new BizOfflineVerify();
        v.setUserId(userId);
        v.setVerifyType(type);
        v.setMaterialUrl(materialUrl);
        v.setApplicantNote(note == null ? "" : note);
        v.setStatus("PENDING");
        v.setOperatorId(0L);
        v.setRemark("");
        offlineVerifyMapper.insert(v);
        return v;
    }

    public PageResult<BizOfflineVerify> offlineVerifies(String status, long page, long size) {
        LambdaQueryWrapper<BizOfflineVerify> q = new LambdaQueryWrapper<>();
        q.eq(status != null && !status.isBlank(), BizOfflineVerify::getStatus,
                status == null ? null : status.toUpperCase());
        q.orderByDesc(BizOfflineVerify::getId);
        return PageResult.of(offlineVerifyMapper.selectPage(Page.of(page, size), q));
    }

    @Transactional
    public BizOfflineVerify reviewOffline(Long operatorId, Long id, boolean passed, String remark) {
        BizOfflineVerify v = offlineVerifyMapper.selectById(id);
        if (v == null) {
            throw BizException.notFound("线下认证申请不存在");
        }
        if (!"PENDING".equals(v.getStatus())) {
            throw BizException.conflict("该申请已处理：" + v.getStatus());
        }
        v.setStatus(passed ? "PASSED" : "REJECTED");
        v.setOperatorId(operatorId);
        v.setRemark(remark == null ? "" : remark);
        v.setVerifiedAt(LocalDateTime.now());
        offlineVerifyMapper.updateById(v);

        if (passed) {
            SysUser u = userMapper.selectById(v.getUserId());
            if (u != null) {
                if ("ID_CARD".equals(v.getVerifyType())) {
                    u.setIdVerified(1);
                } else if ("STUDENT".equals(v.getVerifyType())) {
                    u.setStudentVerified(1);
                } else {
                    u.setIdVerified(1);
                    u.setStudentVerified(1);
                }
                if (u.getIdVerified() != null && u.getIdVerified() == 1
                        && u.getStudentVerified() != null && u.getStudentVerified() == 1) {
                    u.setVerifyState("VERIFIED");
                }
                userMapper.updateById(u);
            }
        }
        noticeService.push(v.getUserId(), "VERIFY", "线下认证结果",
                passed ? "你的线下认证已通过，可以正常交易。" : "线下认证未通过：" + (remark == null ? "材料不清晰" : remark),
                "VERIFY", v.getId());
        return v;
    }

    /** 平台端：查看所有运营人员及其推广业绩。 */
    public PageResult<Map<String, Object>> operators(long page, long size) {
        Page<SysUser> p = userMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, "OPERATOR")
                        .orderByDesc(SysUser::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser u : p.getRecords()) {
            Long invitees = promotionMapper.selectCount(new LambdaQueryWrapper<BizPromotion>()
                    .eq(BizPromotion::getPromoterId, u.getId()));
            List<BizCommission> list = commissionMapper.selectList(new LambdaQueryWrapper<BizCommission>()
                    .eq(BizCommission::getPromoterId, u.getId())
                    .ne(BizCommission::getStatus, "CANCELLED"));
            BigDecimal total = list.stream().map(BizCommission::getAmount)
                    .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("nickname", u.getNickname());
            row.put("phone", maskPhone(u.getPhone()));
            row.put("promoCode", u.getPromoCode());
            row.put("creditScore", u.getCreditScore());
            row.put("inviteeCount", invitees);
            row.put("commissionTotal", total);
            row.put("createdAt", u.getCreatedAt());
            rows.add(row);
        }
        return new PageResult<>(rows, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
