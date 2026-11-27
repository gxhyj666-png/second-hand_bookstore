package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.config.AppProps;
import com.textbook.loop.domain.*;
import com.textbook.loop.mapper.*;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.util.BizNo;
import com.textbook.loop.util.VerifyCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单与担保交易：下单（加锁防超卖）→ 支付（资金冻结）→ 核销（结算给卖家）。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final BizOrdersMapper ordersMapper;
    private final BizListingMapper listingMapper;
    private final SysIdempotentMapper idempotentMapper;
    private final BizPickupPointMapper pointMapper;
    private final BizPickupVerifyMapper verifyMapper;
    private final BizSettlementMapper settlementMapper;
    private final SysUserMapper userMapper;
    private final BizCreditLogMapper creditLogMapper;
    private final NoticeService noticeService;
    private final VerifyCodeUtil verifyCodeUtil;
    private final AppProps props;

    public OrderService(BizOrdersMapper ordersMapper, BizListingMapper listingMapper,
                        SysIdempotentMapper idempotentMapper, BizPickupPointMapper pointMapper,
                        BizPickupVerifyMapper verifyMapper, BizSettlementMapper settlementMapper,
                        SysUserMapper userMapper, BizCreditLogMapper creditLogMapper,
                        NoticeService noticeService, VerifyCodeUtil verifyCodeUtil, AppProps props) {
        this.ordersMapper = ordersMapper;
        this.listingMapper = listingMapper;
        this.idempotentMapper = idempotentMapper;
        this.pointMapper = pointMapper;
        this.verifyMapper = verifyMapper;
        this.settlementMapper = settlementMapper;
        this.userMapper = userMapper;
        this.creditLogMapper = creditLogMapper;
        this.noticeService = noticeService;
        this.verifyCodeUtil = verifyCodeUtil;
        this.props = props;
    }

    @Transactional
    public BizOrders create(Long buyerId, Long listingId, Long pointId, String requestId) {
        String rid = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        SysIdempotent idem = new SysIdempotent();
        idem.setRequestId(rid);
        idem.setScene("CREATE_ORDER");
        idem.setResult("PROCESSING");
        idem.setCreatedAt(LocalDateTime.now());
        try {
            idempotentMapper.insert(idem);
        } catch (DuplicateKeyException e) {
            SysIdempotent old = idempotentMapper.selectById(rid);
            if (old != null && !"PROCESSING".equals(old.getResult())) {
                BizOrders existed = ordersMapper.selectOne(new LambdaQueryWrapper<BizOrders>()
                        .eq(BizOrders::getOrderNo, old.getResult()).last("limit 1"));
                if (existed != null) {
                    return existed;
                }
            }
            throw BizException.conflict("请求重复提交，请勿重复下单");
        }

        BizListing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw BizException.notFound("商品不存在");
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw BizException.badRequest("不能购买自己发布的教材");
        }
        if (!"ON_SALE".equals(listing.getStatus())) {
            throw BizException.conflict("商品已下架或已被下单");
        }
        BizPickupPoint point = pointMapper.selectById(pointId);
        if (point == null) {
            throw BizException.badRequest("请选择有效的自提点");
        }

        // 条件更新加锁：只有仍然 ON_SALE 才能锁住，天然防超卖
        int locked = listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, listingId)
                .eq(BizListing::getStatus, "ON_SALE")
                .set(BizListing::getStatus, "LOCKED")
                .set(BizListing::getUpdatedAt, LocalDateTime.now()));
        if (locked == 0) {
            throw BizException.conflict("手慢了，商品已被他人下单");
        }

        BigDecimal amount = listing.getPrice();
        BigDecimal commission = amount.multiply(BigDecimal.valueOf(props.getCommissionRate()))
                .setScale(2, RoundingMode.HALF_UP);
        String orderNo = BizNo.orderNo();

        BizOrders order = new BizOrders();
        order.setOrderNo(orderNo);
        order.setBuyerId(buyerId);
        order.setSellerId(listing.getSellerId());
        order.setListingId(listingId);
        order.setTextbookId(listing.getTextbookId());
        order.setPointId(pointId);
        order.setAmount(amount);
        order.setCommission(commission);
        order.setStatus("PENDING_PAY");
        order.setVerifyCode(verifyCodeUtil.code(orderNo));
        order.setExpireAt(LocalDateTime.now().plusMinutes(props.getOrderExpireMinutes()));
        ordersMapper.insert(order);

        idem.setResult(orderNo);
        idempotentMapper.updateById(idem);

        noticeService.push(listing.getSellerId(), "ORDER", "有同学下单了",
                "「" + listing.getTitle() + "」已被下单，待买家支付后请按约定送到自提点。",
                "ORDER", order.getId());
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
        if (order.getExpireAt() != null && LocalDateTime.now().isAfter(order.getExpireAt())) {
            closeOrder(order, "支付超时自动关闭");
            throw BizException.conflict("订单已超时关闭，请重新下单");
        }
        order.setStatus("FROZEN");
        order.setPayChannel(channel == null ? "MOCK" : channel);
        order.setTradeNo("MOCK" + System.currentTimeMillis());
        order.setPaidAt(LocalDateTime.now());
        ordersMapper.updateById(order);

        noticeService.push(order.getSellerId(), "ORDER", "买家已支付，资金已冻结",
                "订单 " + orderNo + " 已支付，请尽快把教材送到自提点等待核销。", "ORDER", order.getId());
        noticeService.push(buyerId, "ORDER", "支付成功",
                "订单 " + orderNo + " 支付成功，请凭核销码到自提点取书。", "ORDER", order.getId());
        return order;
    }

    /** 卖家/买家确认教材已送达自提点，订单进入待核销。 */
    @Transactional
    public BizOrders waitVerify(Long operatorId, String orderNo) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(operatorId) && !order.getSellerId().equals(operatorId)
                && !UserContext.isAdmin()) {
            throw BizException.forbidden("无权操作该订单");
        }
        if (!"FROZEN".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可进入待核销：" + order.getStatus());
        }
        order.setStatus("WAIT_VERIFY");
        ordersMapper.updateById(order);

        noticeService.push(order.getBuyerId(), "ORDER", "教材已送达自提点",
                "订单 " + orderNo + " 已可核销，请携带核销码到自提点取书。", "ORDER", order.getId());
        return order;
    }

    @Transactional
    public BizOrders cancel(Long buyerId, String orderNo) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(buyerId)) {
            throw BizException.forbidden("只能取消自己的订单");
        }
        if ("PENDING_PAY".equals(order.getStatus())) {
            closeOrder(order, "买家取消订单");
            return order;
        }
        if ("FROZEN".equals(order.getStatus())) {
            return refundInternal(order, "买家取消，全额退款");
        }
        throw BizException.conflict("订单当前状态不可取消：" + order.getStatus());
    }

    @Transactional
    public BizOrders refund(Long buyerId, String orderNo, String reason) {
        BizOrders order = byNo(orderNo);
        boolean isBuyer = order.getBuyerId().equals(buyerId);
        if (!isBuyer && !UserContext.isAdmin()) {
            throw BizException.forbidden("无权申请退款");
        }
        if (!"FROZEN".equals(order.getStatus()) && !"WAIT_VERIFY".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可退款：" + order.getStatus());
        }
        return refundInternal(order, reason == null ? "买家申请退款" : reason);
    }

    private BizOrders refundInternal(BizOrders order, String reason) {
        order.setStatus("REFUNDED");
        order.setRefundReason(reason);
        order.setClosedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        releaseListing(order.getListingId());
        noticeService.push(order.getSellerId(), "ORDER", "订单已退款",
                "订单 " + order.getOrderNo() + " 已退款，教材已自动重新上架。", "ORDER", order.getId());
        noticeService.push(order.getBuyerId(), "ORDER", "退款成功",
                "订单 " + order.getOrderNo() + " 退款成功，金额将原路退回。", "ORDER", order.getId());
        return order;
    }

    private void closeOrder(BizOrders order, String reason) {
        order.setStatus("CLOSED");
        order.setRemark(reason);
        order.setClosedAt(LocalDateTime.now());
        ordersMapper.updateById(order);
        releaseListing(order.getListingId());
    }

    private void releaseListing(Long listingId) {
        listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, listingId)
                .eq(BizListing::getStatus, "LOCKED")
                .set(BizListing::getStatus, "ON_SALE")
                .set(BizListing::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 核销：店员在自提点校验核销码，资金解冻并结算给卖家。
     */
    @Transactional
    public BizOrders verify(Long operatorId, String orderNo, String code, String method) {
        BizOrders order = byNo(orderNo);
        if ("VERIFIED".equals(order.getStatus()) || "SETTLED".equals(order.getStatus())) {
            throw BizException.conflict("该订单已核销");
        }
        if (!"FROZEN".equals(order.getStatus()) && !"WAIT_VERIFY".equals(order.getStatus())) {
            throw BizException.conflict("订单当前状态不可核销：" + order.getStatus());
        }
        SysUser operator = userMapper.selectById(operatorId);
        if (operator == null || (!"CLERK".equals(operator.getRole()) && !"ADMIN".equals(operator.getRole()))) {
            throw BizException.forbidden("只有自提点店员可以核销");
        }
        boolean ok = verifyCodeUtil.matches(orderNo, code) || order.getVerifyCode().equals(code);
        if (!ok) {
            throw BizException.badRequest("核销码不正确或已过期，请刷新后重试");
        }

        order.setStatus("VERIFIED");
        order.setVerifiedAt(LocalDateTime.now());
        ordersMapper.updateById(order);

        BizListing listing = listingMapper.selectById(order.getListingId());
        listingMapper.update(null, new LambdaUpdateWrapper<BizListing>()
                .eq(BizListing::getId, order.getListingId())
                .set(BizListing::getStatus, "SOLD")
                .set(BizListing::getUpdatedAt, LocalDateTime.now()));

        BizPickupVerify pv = new BizPickupVerify();
        pv.setOrderId(order.getId());
        pv.setOrderNo(orderNo);
        pv.setPointId(order.getPointId());
        pv.setOperatorId(operatorId);
        pv.setCode(code);
        pv.setMethod(method == null ? "SCAN" : method);
        pv.setVerifiedAt(LocalDateTime.now());
        verifyMapper.insert(pv);

        // 结算：订单金额 - 服务费
        BigDecimal settleAmount = order.getAmount().subtract(order.getCommission());
        BizSettlement st = new BizSettlement();
        st.setOrderId(order.getId());
        st.setOrderNo(orderNo);
        st.setSellerId(order.getSellerId());
        st.setAmount(settleAmount);
        st.setCommission(order.getCommission());
        st.setStatus("DONE");
        st.setBatchNo(BizNo.batchNo());
        st.setSettleAt(LocalDateTime.now());
        st.setCreatedAt(LocalDateTime.now());
        settlementMapper.insert(st);

        order.setStatus("SETTLED");
        order.setSettledAt(LocalDateTime.now());
        ordersMapper.updateById(order);

        addCredit(order.getBuyerId(), 5, "DEAL_DONE", "ORDER", order.getId());
        addCredit(order.getSellerId(), 5, "DEAL_DONE", "ORDER", order.getId());

        String bookTitle = listing == null ? "教材" : listing.getTitle();
        noticeService.push(order.getBuyerId(), "ORDER", "交易完成",
                "「" + bookTitle + "」已完成核销，感谢使用同窗书。", "ORDER", order.getId());
        noticeService.push(order.getSellerId(), "CREDIT", "货款已结算",
                "「" + bookTitle + "」已核销，¥" + settleAmount + " 已结算（服务费 ¥" + order.getCommission() + "）。",
                "ORDER", order.getId());
        log.info("order {} verified by operator {}, settlement {}", orderNo, operatorId, settleAmount);
        return order;
    }

    private void addCredit(Long userId, int delta, String reason, String refType, Long refId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int before = user.getCreditScore() == null ? 100 : user.getCreditScore();
        int after = Math.max(0, before + delta);
        user.setCreditScore(after);
        userMapper.updateById(user);

        BizCreditLog log = new BizCreditLog();
        log.setUserId(userId);
        log.setChangeVal(delta);
        log.setScoreAfter(after);
        log.setReason(reason);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setCreatedAt(LocalDateTime.now());
        creditLogMapper.insert(log);
    }

    public PageResult<BizOrders> list(Long userId, String role, String status, long page, long size) {
        boolean asSeller = "seller".equalsIgnoreCase(role);
        LambdaQueryWrapper<BizOrders> q = new LambdaQueryWrapper<BizOrders>()
                .eq(asSeller ? BizOrders::getSellerId : BizOrders::getBuyerId, userId)
                .eq(status != null && !status.isBlank(), BizOrders::getStatus, status)
                .orderByDesc(BizOrders::getId);
        return PageResult.of(ordersMapper.selectPage(Page.of(page, size), q));
    }

    public BizOrders detail(String orderNo, Long userId) {
        BizOrders order = byNo(orderNo);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId) && !UserContext.isAdmin()) {
            throw BizException.forbidden("无权查看该订单");
        }
        return order;
    }

    /**
     * 当前核销码 + 剩余有效秒数（买家在详情页 30 秒轮换展示）。
     */
    public java.util.Map<String, Object> verifyCode(String orderNo, Long userId) {
        BizOrders order = detail(orderNo, userId);
        // 只有已支付/待核销的订单才该出码，否则未付款也能在页面上看到动态码
        if (!"FROZEN".equals(order.getStatus()) && !"WAIT_VERIFY".equals(order.getStatus())) {
            throw com.textbook.loop.common.BizException.conflict("订单当前状态不可出示核销码：" + order.getStatus());
        }
        return java.util.Map.of(
                "code", verifyCodeUtil.code(orderNo),
                "remainSeconds", verifyCodeUtil.remainSeconds(),
                "status", order.getStatus());
    }

    /**
     * 超时未支付自动关单（幂等，可重复执行）。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    @Transactional
    public void closeExpiredOrders() {
        List<BizOrders> expired = ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getStatus, "PENDING_PAY")
                .lt(BizOrders::getExpireAt, LocalDateTime.now())
                .last("limit 50"));
        for (BizOrders order : expired) {
            closeOrder(order, "支付超时自动关闭");
            noticeService.push(order.getBuyerId(), "ORDER", "订单已关闭",
                    "订单 " + order.getOrderNo() + " 超过支付时限已自动关闭。", "ORDER", order.getId());
            log.info("expired order closed: {}", order.getOrderNo());
        }
    }
}
