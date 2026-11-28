package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.config.AppProps;
import com.textbook.loop.domain.*;
import com.textbook.loop.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 交易主流程（打磨后的三态模型）：
 *
 * <pre>
 *   下单 PENDING_PAY（仅维持 5 分钟，超时自动关闭）
 *     → 支付 WAIT_VERIFY（卖家可生成 6 位核销码，15 分钟有效，且不晚于约定时间后 15 分钟）
 *     → 线下见面，卖家输入核销码 + 上传签到照片 → COMPLETED（商品 SOLD、结算、双方信用 +5）
 *
 *   取消：待支付买卖双方均可取消；已付款未核销可取消（买家/卖家，责任方扣 3 分）
 *   退货：REFUNDING（RETURN 退货退款 / REFUND_ONLY 仅退款）→ 卖家同意 → 退款
 *         卖家拒绝 → 买家申请介入 ARBITRATING → 平台判定：强制退款（卖家 -5）或交易维持（买家 -5）
 *
 *   爽约：约定时间 +30 分钟后可上报，被上报方 -5 信用分。
 * </pre>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BizOrdersMapper ordersMapper;
    private final BizListingMapper listingMapper;
    private final SysIdempotentMapper idempotentMapper;
    private final BizPickupPointMapper pointMapper;
    private final BizPickupVerifyMapper verifyMapper;
    private final BizSettlementMapper settlementMapper;
    private final BizMeetMapper meetMapper;
    private final BizRefundMapper refundMapper;
    private final BizNoShowMapper noShowMapper;
    private final BizPromotionMapper promotionMapper;
    private final BizCommissionMapper commissionMapper;
    private final SysUserMapper userMapper;
    private final BizCreditLogMapper creditLogMapper;
    private final NoticeService noticeService;
    private final IdentityService identityService;
    private final AppProps props;

    public OrderService(BizOrdersMapper ordersMapper, BizListingMapper listingMapper,
                        SysIdempotentMapper idempotentMapper, BizPickupPointMapper pointMapper,
                        BizPickupVerifyMapper verifyMapper, BizSettlementMapper settlementMapper,
                        BizMeetMapper meetMapper, BizRefundMapper refundMapper, BizNoShowMapper noShowMapper,
                        BizPromotionMapper promotionMapper, BizCommissionMapper commissionMapper,
                        SysUserMapper userMapper, BizCreditLogMapper creditLogMapper,
                        NoticeService noticeService, IdentityService identityService, AppProps props) {
        this.ordersMapper = ordersMapper;
        this.listingMapper = listingMapper;
        this.idempotentMapper = idempotentMapper;
        this.pointMapper = pointMapper;
        this.verifyMapper = verifyMapper;
        this.settlementMapper = settlementMapper;
        this.meetMapper = meetMapper;
        this.refundMapper = refundMapper;
        this.noShowMapper = noShowMapper;
        this.promotionMapper = promotionMapper;
        this.commissionMapper = commissionMapper;
        this.userMapper = userMapper;
        this.creditLogMapper = creditLogMapper;
        this.noticeService = noticeService;
        this.identityService = identityService;
        this.props = props;
    }

    // ------------------------------------------------------------------ 下单/支付

    @Transactional
    public BizOrders create(Long buyerId, Long listingId, Long pointId, String requestId) {
        identityService.requireTradePermission(buyerId);
        String scene = "CREATE_ORDER";
        if (requestId != null && !requestId.isBlank()) {
            SysIdempotent key = new SysIdempotent();
            key.setRequestId(requestId);
            key.setScene(scene);
            key.setResult("");
            try {
                idempotentMapper.insert(key);
            } catch (DuplicateKeyException e) {
                SysIdempotent exist = idempotentMapper.selectById(requestId);
                if (exist != null && exist.getResult() != null && !exist.getResult().isBlank()) {
                    BizOrders done = ordersMapper.selectById(Long.valueOf(exist.getResult()));
                    if (done != null) {
                        return done;
                    }
                }
                throw BizException.conflict("请求正在处理，请勿重复提交");
            }
        }

        BizListing listing = listingMapper.selectById(listingId);
        if (listing == null || !"ON_SALE".equals(listing.getStatus())) {
            throw BizException.conflict("商品已下架或已被预订");
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw BizException.badRequest("不能购买自己发布的商品");
        }
        int locked = listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, listingId)
                .eq(BizListing::getStatus, "ON_SALE")
                .set(BizListing::getStatus, "LOCKED"));
        if (locked == 0) {
            throw BizException.conflict("商品已被其他人抢先下单");
        }

        BizOrders order = new BizOrders();
        order.setOrderNo("TB" + LocalDateTime.now().format(NO_FMT) + String.format("%06d", RANDOM.nextInt(1_000_000)));
        order.setBuyerId(buyerId);
        order.setSellerId(listing.getSellerId());
        order.setListingId(listing.getId());
        order.setTextbookId(listing.getTextbookId());
        order.setMerchantId(listing.getMerchantId() == null ? 0L : listing.getMerchantId());
        if (pointId != null && pointId > 0) {
            BizPickupPoint point = pointMapper.selectById(pointId);
            if (point == null) {
                throw BizException.notFound("自提点不存在");
            }
            order.setPointId(pointId);
            order.setMeetPlace(point.getAddress());
        } else {
            order.setPointId(0L);
            order.setMeetPlace("");
        }
        order.setAmount(listing.getPrice());
        order.setCommission(java.math.BigDecimal.ZERO);
        order.setStatus("PENDING_PAY");
        order.setVerifyCode("");
        order.setPayChannel("");
        order.setRemark("");
        order.setExpireAt(LocalDateTime.now().plusMinutes(props.getOrderExpireMinutes()));
        ordersMapper.insert(order);

        if (requestId != null && !requestId.isBlank()) {
            idempotentMapper.update(null, new LambdaUpdateWrapper<SysIdempotent>()
                    .eq(SysIdempotent::getRequestId, requestId)
                    .eq(SysIdempotent::getScene, scene)
                    .set(SysIdempotent::getResult, String.valueOf(order.getId())));
        }
        noticeService.push(listing.getSellerId(), "ORDER", "收到新订单",
                "有人下单了《" + listing.getTitle() + "》，请尽快与买家约定见面时间地点。", "ORDER", order.getId());
        return order;
    }

    public BizOrders byNo(String orderNo) {
        BizOrders order = ordersMapper.selectOne(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getOrderNo, orderNo).last("limit 1"));
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    @Transactional
    public BizOrders pay(Long buyerId, String orderNo, String channel) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(buyerId)) {
            throw BizException.forbidden("只能支付自己的订单");
        }
        if (!"PENDING_PAY".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可支付：" + order.getStatus());
        }
        if (order.getExpireAt() != null && order.getExpireAt().isBefore(LocalDateTime.now())) {
            closeOrder(order, "超时未支付自动关闭", "TIMEOUT");
            throw BizException.conflict("订单已超时关闭");
        }
        order.setStatus("WAIT_VERIFY");
        order.setPaidAt(LocalDateTime.now());
        order.setPayChannel(channel == null || channel.isBlank() ? "MOCK" : channel);
        ordersMapper.updateById(order);
        noticeService.push(order.getSellerId(), "ORDER", "买家已支付",
                "订单 " + orderNo + " 已支付，请与买家约定见面时间地点，见面后由你输入核销码完成交易。", "ORDER", order.getId());
        return order;
    }

    @Transactional
    public BizOrders cancel(Long operatorId, String orderNo) {
        BizOrders order = byNo(orderNo);
        boolean isBuyer = order.getBuyerId().equals(operatorId);
        boolean isSeller = order.getSellerId().equals(operatorId);
        if (!isBuyer && !isSeller) {
            throw BizException.forbidden("无权取消该订单");
        }
        LocalDateTime now = LocalDateTime.now();
        if ("PENDING_PAY".equals(order.getStatus())) {
            closeOrder(order, isBuyer ? "买家取消订单" : "卖家取消订单",
                    isBuyer ? "BUYER_CANCEL" : "SELLER_CANCEL");
            return order;
        }
        if ("WAIT_VERIFY".equals(order.getStatus())) {
            boolean meetPassed = order.getMeetTime() != null && now.isAfter(order.getMeetTime());
            String closeType = isBuyer ? "PAID_CANCEL" : "SELLER_CANCEL";
            closeOrder(order,
                    isBuyer ? "买家付款后取消，款项原路退回" : "卖家取消交易，款项原路退回", closeType);
            noticeService.push(isBuyer ? order.getSellerId() : order.getBuyerId(), "ORDER", "订单已取消",
                    "订单 " + orderNo + " 已取消，款项原路退回买家。", "ORDER", order.getId());
            if (isSeller) {
                addCredit(order.getSellerId(), -3, "SELLER_CANCEL", "卖家取消已付款订单", "ORDER", order.getId());
            } else if (meetPassed) {
                addCredit(order.getBuyerId(), -3, "PAID_CANCEL", "约定时间已到仍取消交易", "ORDER", order.getId());
            }
            return order;
        }
        throw BizException.conflict("订单当前状态不可取消：" + order.getStatus());
    }

    // ------------------------------------------------------------------ 核销码

    /**
     * 核销码硬截止：约定交易时间 + 15 分钟。未约定时间时以生成时间 + 15 分钟为准。
     */
    private LocalDateTime codeDeadline(BizOrders order, LocalDateTime now) {
        int ttl = props.getVerifyCodeTtlMinutes();
        LocalDateTime ttlDeadline = now.plusMinutes(ttl);
        LocalDateTime meetDeadline = order.getMeetTime() == null ? null : order.getMeetTime().plusMinutes(ttl);
        if (meetDeadline == null) {
            return ttlDeadline;
        }
        return meetDeadline.isBefore(ttlDeadline) ? meetDeadline : ttlDeadline;
    }

    /** 生成一枚新核销码（卖家发起 / 过期换新），有效期取「15 分钟」与「约定时间 +15 分钟」的较小者。 */
    private void issueCode(BizOrders order) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getMeetTime() != null && now.isAfter(order.getMeetTime().plusMinutes(props.getVerifyCodeTtlMinutes()))) {
            throw BizException.conflict("已超过约定交易时间 15 分钟，请先与买家重新约定时间");
        }
        order.setVerifyCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        order.setVerifyExpireAt(codeDeadline(order, now));
        ordersMapper.updateById(order);
    }

    /**
     * 买家出示核销码：6 位数字，15 分钟有效；过期后再次获取会换新码。
     * 但超过约定交易时间 15 分钟后不再换新，需由卖家重新生成。
     */
    @Transactional
    public Map<String, Object> verifyCode(String orderNo, Long userId) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(userId)) {
            throw BizException.forbidden("只有买家可以出示核销码");
        }
        if (!"WAIT_VERIFY".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可出示核销码：" + order.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getMeetTime() != null && now.isAfter(order.getMeetTime().plusMinutes(props.getVerifyCodeTtlMinutes()))) {
            throw BizException.conflict("已超过约定交易时间 15 分钟，核销码已失效，请联系卖家重新生成");
        }
        boolean expired = order.getVerifyExpireAt() == null || order.getVerifyExpireAt().isBefore(now)
                || order.getVerifyCode() == null || order.getVerifyCode().length() != 6;
        if (expired) {
            issueCode(order);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", order.getVerifyCode());
        map.put("status", order.getStatus());
        map.put("remainSeconds", Math.max(0, Duration.between(now, order.getVerifyExpireAt()).getSeconds()));
        map.put("expireAt", order.getVerifyExpireAt());
        map.put("ttlMinutes", props.getVerifyCodeTtlMinutes());
        map.put("meetTime", order.getMeetTime());
        return map;
    }

    /** 卖家（或商户）重新生成核销码：约定时间过后 15 分钟内仍可重发，重发后原码立即作废。 */
    @Transactional
    public Map<String, Object> regenerateVerifyCode(Long operatorId, String orderNo) {
        BizOrders order = byNo(orderNo);
        SysUser operator = userMapper.selectById(operatorId);
        boolean admin = operator != null && "ADMIN".equals(operator.getRole());
        if (!order.getSellerId().equals(operatorId) && !admin) {
            throw BizException.forbidden("只有卖家可以重新生成核销码");
        }
        if (!"WAIT_VERIFY".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可生成核销码：" + order.getStatus());
        }
        issueCode(order);
        noticeService.push(order.getBuyerId(), "ORDER", "核销码已重新生成",
                "订单 " + orderNo + " 的核销码已由卖家重新生成，请重新出示（15 分钟内有效）。", "ORDER", order.getId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", order.getVerifyCode());
        map.put("expireAt", order.getVerifyExpireAt());
        map.put("remainSeconds", Math.max(0, Duration.between(LocalDateTime.now(), order.getVerifyExpireAt()).getSeconds()));
        return map;
    }

    /** 卖家线下核销：核对 6 位码与有效期，记录签到照片，订单完成、商品售出、结算、双方信用 +5。 */
    @Transactional
    public BizOrders verify(Long operatorId, String orderNo, String code, String signPhotoUrl) {
        BizOrders order = byNo(orderNo);
        SysUser operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw BizException.notFound("操作人不存在");
        }
        boolean admin = "ADMIN".equals(operator.getRole());
        if (!order.getSellerId().equals(operatorId) && !admin) {
            throw BizException.forbidden("只能由卖家核销");
        }
        if (!"WAIT_VERIFY".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可核销：" + order.getStatus());
        }
        if (order.getVerifyCode() == null || order.getVerifyCode().isBlank()) {
            throw BizException.conflict("买家尚未生成核销码");
        }
        if (order.getMeetTime() != null
                && LocalDateTime.now().isAfter(order.getMeetTime().plusMinutes(props.getVerifyCodeTtlMinutes()))) {
            throw BizException.conflict("已超过约定交易时间 15 分钟，核销码失效，请让卖家重新生成");
        }
        if (order.getVerifyExpireAt() == null || order.getVerifyExpireAt().isBefore(LocalDateTime.now())) {
            throw BizException.conflict("核销码已过期，请让卖家重新生成");
        }
        if (code == null || !code.trim().equals(order.getVerifyCode())) {
            throw BizException.conflict("核销码不正确");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus("COMPLETED");
        order.setCompletedAt(now);
        order.setSignAt(now);
        order.setSignPhotoUrl(signPhotoUrl == null ? "" : signPhotoUrl);
        order.setVerifyCode("");
        ordersMapper.updateById(order);

        listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, order.getListingId())
                .set(BizListing::getStatus, "SOLD"));

        BizPickupVerify pv = new BizPickupVerify();
        pv.setOrderId(order.getId());
        pv.setOrderNo(order.getOrderNo());
        pv.setPointId(order.getPointId() == null ? 0L : order.getPointId());
        pv.setOperatorId(operatorId);
        pv.setCode("");
        pv.setMethod("SELLER_SIGN");
        ordersMapper.selectById(order.getId());
        verifyMapper.insert(pv);

        BizSettlement settlement = new BizSettlement();
        settlement.setOrderId(order.getId());
        settlement.setOrderNo(order.getOrderNo());
        settlement.setSellerId(order.getSellerId());
        settlement.setAmount(order.getAmount());
        settlement.setCommission(java.math.BigDecimal.ZERO);
        settlement.setStatus("DONE");
        settlement.setSettleAt(now);
        settlementMapper.insert(settlement);

        addCredit(order.getBuyerId(), 5, "DEAL_DONE", "完成一次交易", "ORDER", order.getId());
        addCredit(order.getSellerId(), 5, "DEAL_DONE", "完成一次交易", "ORDER", order.getId());
        settlePromotion(order);
        noticeService.push(order.getBuyerId(), "ORDER", "交易完成",
                "订单 " + orderNo + " 已完成，记得互相评价。", "ORDER", order.getId());
        return order;
    }

    /** 下线买家完成交易后，按成交额给运营计提推广提成（同一订单只记一次）。 */
    private void settlePromotion(BizOrders order) {
        BizPromotion promo = promotionMapper.selectOne(new LambdaQueryWrapper<BizPromotion>()
                .eq(BizPromotion::getInviteeId, order.getBuyerId()).last("limit 1"));
        if (promo == null) {
            return;
        }
        BigDecimal rate = BigDecimal.valueOf(props.getPromoterRate()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO
                : order.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BizCommission c = new BizCommission();
        c.setPromoterId(promo.getPromoterId());
        c.setOrderId(order.getId());
        c.setOrderNo(order.getOrderNo());
        c.setOrderAmount(order.getAmount());
        c.setRate(rate);
        c.setAmount(amount);
        c.setStatus("PENDING");
        try {
            commissionMapper.insert(c);
        } catch (DuplicateKeyException ignore) {
            log.debug("提成已存在 orderNo={}", order.getOrderNo());
        }
    }

    // ------------------------------------------------------------------ 约见

    /** 卖家/买家发起约见（TRADE 交易 / RETURN 退货）：地点 + 时间，对方确认生效。 */
    @Transactional
    public BizMeet proposeMeet(Long operatorId, String orderNo, String type, String place, LocalDateTime time) {
        BizOrders order = byNo(orderNo);
        boolean isSeller = order.getSellerId().equals(operatorId);
        boolean isBuyer = order.getBuyerId().equals(operatorId);
        if (!isSeller && !isBuyer) {
            throw BizException.forbidden("无权发起该订单的约见");
        }
        if (place == null || place.isBlank()) {
            throw BizException.badRequest("请填写见面地点");
        }
        if (time == null) {
            throw BizException.badRequest("请选择见面时间");
        }
        String t = (type == null || type.isBlank()) ? "TRADE" : type.toUpperCase();
        BizMeet meet = new BizMeet();
        meet.setOrderId(order.getId());
        meet.setMeetType(t);
        meet.setProposerId(operatorId);
        meet.setPlace(place.trim());
        meet.setMeetTime(time);
        meet.setRemark("");
        meet.setStatus("PROPOSED");
        meetMapper.insert(meet);
        Long target = isSeller ? order.getBuyerId() : order.getSellerId();
        noticeService.push(target, "MEET", "新的见面时间地点待确认",
                "订单 " + orderNo + " 约在 " + place + " / " + time + "，请确认。", "ORDER", order.getId());
        return meet;
    }

    @Transactional
    public BizMeet confirmMeet(Long operatorId, Long meetId, boolean agreed, String note) {
        BizMeet meet = meetMapper.selectById(meetId);
        if (meet == null) {
            throw BizException.notFound("约见记录不存在");
        }
        BizOrders order = ordersMapper.selectById(meet.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        boolean participant = order.getBuyerId().equals(operatorId) || order.getSellerId().equals(operatorId);
        if (!participant) {
            throw BizException.forbidden("无权确认该约见");
        }
        if (meet.getProposerId().equals(operatorId)) {
            throw BizException.conflict("发起方无需确认，请等待对方");
        }
        if (!"PROPOSED".equals(meet.getStatus())) {
            throw BizException.conflict("该约见已处理：" + meet.getStatus());
        }
        meet.setStatus(agreed ? "CONFIRMED" : "CANCELED");
        meet.setRemark(note == null ? "" : note);
        meet.setConfirmedAt(LocalDateTime.now());
        meetMapper.updateById(meet);
        if (agreed && "TRADE".equals(meet.getMeetType())) {
            order.setMeetPlace(meet.getPlace());
            order.setMeetTime(meet.getMeetTime());
            ordersMapper.updateById(order);
        }
        noticeService.push(meet.getProposerId(), "MEET", agreed ? "对方已确认见面" : "对方拒绝了约见",
                meet.getPlace() + " / " + meet.getMeetTime(), "ORDER", meet.getOrderId());
        return meet;
    }

    public List<BizMeet> meets(String orderNo, Long userId) {
        BizOrders order = detail(orderNo, userId);
        return meetMapper.selectList(new LambdaQueryWrapper<BizMeet>()
                .eq(BizMeet::getOrderId, order.getId())
                .orderByDesc(BizMeet::getId));
    }

    // ------------------------------------------------------------------ 退货/介入

    @Transactional
    public BizRefund applyRefund(Long buyerId, String orderNo, String reason, String photoUrl, String refundType) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(buyerId)) {
            throw BizException.forbidden("只有买家可以申请退货");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw BizException.conflict("只有已完成的订单可以申请退货，当前：" + order.getStatus());
        }
        if (order.getCompletedAt() != null && order.getCompletedAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw BizException.conflict("已超过 7 天售后期限");
        }
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("请填写退款原因");
        }
        String type = "REFUND_ONLY".equalsIgnoreCase(refundType) ? "REFUND_ONLY" : "RETURN";
        BizRefund refund = new BizRefund();
        refund.setOrderId(order.getId());
        refund.setApplicantId(buyerId);
        refund.setRefundType(type);
        refund.setReason(reason.trim());
        refund.setEvidenceUrl(photoUrl == null ? "" : photoUrl);
        refund.setAmount(order.getAmount());
        refund.setStatus("PENDING");
        refund.setPlatformVerdict("");
        refund.setPlatformRemark("");
        refundMapper.insert(refund);

        order.setStatus("REFUNDING");
        order.setRefundReason(reason.trim());
        ordersMapper.updateById(order);
        noticeService.push(order.getSellerId(), "REFUND", "REFUND_ONLY".equals(type) ? "买家申请仅退款" : "买家申请退货退款",
                "订单 " + orderNo + " 买家申请" + ("REFUND_ONLY".equals(type) ? "仅退款" : "退货退款")
                        + "：" + reason, "ORDER", order.getId());
        return refund;
    }

    @Transactional
    public BizRefund agreeRefund(Long sellerId, Long refundId) {
        BizRefund refund = requireRefund(refundId);
        BizOrders order = ordersMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw BizException.forbidden("只有卖家可以同意退货");
        }
        if (!"PENDING".equals(refund.getStatus())) {
            throw BizException.conflict("该退货申请已处理：" + refund.getStatus());
        }
        refund.setStatus("AGREED");
        refund.setDecidedBy(sellerId);
        refund.setDecidedAt(LocalDateTime.now());
        refundMapper.updateById(refund);
        if ("REFUND_ONLY".equalsIgnoreCase(refund.getRefundType())) {
            refund.setStatus("REFUNDED");
            refund.setRefundedAt(LocalDateTime.now());
            refundMapper.updateById(refund);
            markRefunded(order, "卖家已同意仅退款，款项原路退回");
            return refund;
        }
        order.setStatus("REFUND_AGREED");
        ordersMapper.updateById(order);
        noticeService.push(order.getBuyerId(), "REFUND", "卖家已同意退货",
                "请与卖家确认退货时间地点，退回商品后点击完成退货。", "ORDER", order.getId());
        return refund;
    }

    @Transactional
    public BizRefund rejectRefund(Long sellerId, Long refundId, String reason) {
        BizRefund refund = requireRefund(refundId);
        BizOrders order = ordersMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw BizException.forbidden("只有卖家可以拒绝退货");
        }
        if (!"PENDING".equals(refund.getStatus())) {
            throw BizException.conflict("该退货申请已处理：" + refund.getStatus());
        }
        refund.setStatus("REJECTED");
        refund.setDecidedBy(sellerId);
        refund.setDecidedAt(LocalDateTime.now());
        refund.setPlatformRemark(reason == null ? "" : reason);
        refundMapper.updateById(refund);
        order.setStatus("COMPLETED");
        ordersMapper.updateById(order);
        noticeService.push(order.getBuyerId(), "REFUND", "卖家拒绝了退货",
                "理由：" + (reason == null ? "无" : reason) + "。如不认可，可申请平台介入。", "ORDER", order.getId());
        return refund;
    }

    /** 买家确认退货完成（卖家已收货/线下退回）：钱款原路退回，商品回到在售。 */
    @Transactional
    public BizRefund finishRefund(Long operatorId, Long refundId) {
        BizRefund refund = requireRefund(refundId);
        BizOrders order = ordersMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (!refund.getApplicantId().equals(operatorId) && !isAdmin(operatorId)) {
            throw BizException.forbidden("只有买家可以确认退货完成");
        }
        if (!"AGREED".equals(refund.getStatus())) {
            throw BizException.conflict("退货尚未被卖家同意");
        }
        refund.setStatus("ARBITRATED");
        refund.setPlatformVerdict("REFUNDED");
        refund.setRefundedAt(LocalDateTime.now());
        refundMapper.updateById(refund);
        markRefunded(order, "买家确认退货完成，钱款原路退回");
        return refund;
    }

    @Transactional
    public BizRefund requestArbitration(Long buyerId, Long refundId, String note) {
        BizRefund refund = requireRefund(refundId);
        BizOrders order = ordersMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (!refund.getApplicantId().equals(buyerId)) {
            throw BizException.forbidden("只有买家可以申请介入");
        }
        if (!"PENDING".equals(refund.getStatus()) && !"REJECTED".equals(refund.getStatus())) {
            throw BizException.conflict("当前状态不可申请介入：" + refund.getStatus());
        }
        refund.setStatus("ARBITRATING");
        refund.setPlatformRemark(note == null ? "" : note);
        refundMapper.updateById(refund);
        order.setStatus("ARBITRATING");
        ordersMapper.updateById(order);
        noticeService.push(order.getSellerId(), "REFUND", "买家申请平台介入",
                "平台将介入判定，请保持沟通畅通。", "ORDER", order.getId());
        return refund;
    }

    /** 平台判定：买家胜 → 强制退款；卖家胜 → 交易维持完成。 */
    @Transactional
    public BizRefund arbitrate(Long adminId, Long refundId, boolean buyerWin, String note) {
        BizRefund refund = requireRefund(refundId);
        BizOrders order = ordersMapper.selectById(refund.getOrderId());
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        if (!"ARBITRATING".equals(refund.getStatus())) {
            throw BizException.conflict("该退货不在仲裁中：" + refund.getStatus());
        }
        refund.setStatus("ARBITRATED");
        refund.setPlatformVerdict(buyerWin ? "SELLER_FAULT" : "PLATFORM_OK");
        refund.setPlatformRemark(note == null ? "" : note);
        refund.setDecidedBy(adminId);
        refund.setDecidedAt(LocalDateTime.now());
        refundMapper.updateById(refund);
        if (buyerWin) {
            addCredit(order.getSellerId(), -5, "ARBITRATION_SELLER_FAULT",
                    "平台判定卖家责任（商品与描述不符等）", "REFUND", refund.getId());
            markRefunded(order, "平台判定支持买家，强制退款");
        } else {
            addCredit(order.getBuyerId(), -5, "ARBITRATION_BUYER_FAULT",
                    "平台判定买家责任（人为损坏等）", "REFUND", refund.getId());
            order.setStatus("COMPLETED");
            ordersMapper.updateById(order);
            noticeService.push(order.getBuyerId(), "REFUND", "平台判定结果",
                    "平台判定货物无问题，交易维持完成。", "ORDER", order.getId());
        }
        return refund;
    }

    /** 爽约上报：约定时间过 30 分钟后可上报，被上报方 -5 信用分。 */
    @Transactional
    public Map<String, Object> reportNoShow(Long reporterId, String orderNo, String reason) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(reporterId) && !order.getSellerId().equals(reporterId)) {
            throw BizException.forbidden("只有交易双方可以上报爽约");
        }
        if ("COMPLETED".equals(order.getStatus()) || "CLOSED".equals(order.getStatus())) {
            throw BizException.conflict("订单已结束，无法上报爽约");
        }
        if (order.getMeetTime() == null) {
            throw BizException.conflict("尚未确认见面时间，无法上报爽约");
        }
        if (LocalDateTime.now().isBefore(order.getMeetTime().plusMinutes(30))) {
            throw BizException.conflict("约定时间过后 30 分钟才可上报");
        }
        Long target = order.getBuyerId().equals(reporterId) ? order.getSellerId() : order.getBuyerId();
        BizNoShow ns = new BizNoShow();
        ns.setOrderId(order.getId());
        ns.setReporterId(reporterId);
        ns.setTargetId(target);
        ns.setPhotoUrl("");
        ns.setDescription(reason == null ? "" : reason);
        ns.setCreditDelta(-5);
        ns.setStatus("VALID");
        noShowMapper.insert(ns);
        addCredit(target, -5, "NO_SHOW", "约定时间未到且未沟通", "ORDER", order.getId());
        noticeService.push(target, "ORDER", "爽约记录",
                "订单 " + orderNo + " 被上报爽约，已扣 5 信誉分。如有异议可联系平台。", "ORDER", order.getId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recorded", true);
        map.put("targetUserId", target);
        map.put("creditDelta", -5);
        return map;
    }

    // ------------------------------------------------------------------ 查询/定时

    public PageResult<BizOrders> list(Long userId, String role, String status, long page, long size) {
        LambdaQueryWrapper<BizOrders> qw = new LambdaQueryWrapper<>();
        if ("seller".equalsIgnoreCase(role)) {
            qw.eq(BizOrders::getSellerId, userId);
        } else {
            qw.eq(BizOrders::getBuyerId, userId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(BizOrders::getStatus, status.toUpperCase());
        }
        qw.orderByDesc(BizOrders::getId);
        Page<BizOrders> p = ordersMapper.selectPage(new Page<>(page, size), qw);
        return new PageResult<>(p.getRecords(), p.getTotal(), page, size);
    }

    public BizOrders detail(String orderNo, Long userId) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId) && !isAdmin(userId)) {
            throw BizException.forbidden("无权查看该订单");
        }
        return order;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void closeExpiredOrders() {
        List<BizOrders> list = ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getStatus, "PENDING_PAY")
                .lt(BizOrders::getExpireAt, LocalDateTime.now()));
        for (BizOrders order : list) {
            try {
                closeOrder(order, "超时未支付自动关闭", "TIMEOUT");
            } catch (Exception e) {
                log.warn("关闭订单失败 orderNo={} err={}", order.getOrderNo(), e.getMessage());
            }
        }
        if (!list.isEmpty()) {
            log.info("自动关闭超时订单 {} 笔", list.size());
        }
    }

    // ------------------------------------------------------------------ 内部

    private BizRefund requireRefund(Long refundId) {
        BizRefund refund = refundMapper.selectById(refundId);
        if (refund == null) {
            throw BizException.notFound("退货申请不存在");
        }
        return refund;
    }

    private void markRefunded(BizOrders order, String note) {
        order.setStatus("REFUNDED");
        ordersMapper.updateById(order);
        commissionMapper.update(null, new LambdaUpdateWrapper<BizCommission>()
                .eq(BizCommission::getOrderId, order.getId())
                .eq(BizCommission::getStatus, "PENDING")
                .set(BizCommission::getStatus, "CANCELLED"));
        listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, order.getListingId())
                .set(BizListing::getStatus, "ON_SALE"));
        noticeService.push(order.getBuyerId(), "REFUND", "退款已完成",
                "订单 " + order.getOrderNo() + " " + note, "ORDER", order.getId());
        noticeService.push(order.getSellerId(), "REFUND", "退款已完成",
                "订单 " + order.getOrderNo() + " " + note, "ORDER", order.getId());
    }

    private void closeOrder(BizOrders order, String reason, String closeType) {
        order.setStatus("CLOSED");
        order.setCloseType(closeType == null ? "" : closeType);
        order.setClosedAt(LocalDateTime.now());
        order.setRemark(reason);
        ordersMapper.updateById(order);
        releaseListing(order.getListingId());
        noticeService.push(order.getBuyerId(), "ORDER", "订单已关闭",
                "订单 " + order.getOrderNo() + "：" + reason, "ORDER", order.getId());
    }

    private void releaseListing(Long listingId) {
        if (listingId == null) {
            return;
        }
        listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, listingId)
                .eq(BizListing::getStatus, "LOCKED")
                .set(BizListing::getStatus, "ON_SALE"));
    }

    private void addCredit(Long userId, int delta, String reason, String detail, String refType, Long refId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int score = Math.min(150, Math.max(0, (user.getCreditScore() == null ? 100 : user.getCreditScore()) + delta));
        user.setCreditScore(score);
        userMapper.updateById(user);

        BizCreditLog logRow = new BizCreditLog();
        logRow.setUserId(userId);
        logRow.setChangeVal(delta);
        logRow.setScoreAfter(score);
        logRow.setReason(reason);
        logRow.setRefType(refType);
        logRow.setRefId(refId);
        creditLogMapper.insert(logRow);
        log.debug("credit {} {} -> {} ({})", userId, delta, score, detail);
    }

    private boolean isAdmin(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        return user != null && "ADMIN".equals(user.getRole());
    }
}
