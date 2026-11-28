package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizReview;
import com.textbook.loop.domain.BizSettlement;
import com.textbook.loop.domain.SysMerchant;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizListingMapper;
import com.textbook.loop.mapper.BizOrdersMapper;
import com.textbook.loop.mapper.BizReviewMapper;
import com.textbook.loop.mapper.BizSettlementMapper;
import com.textbook.loop.mapper.SysMerchantMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商户端：入驻申请、批量上下架、商户看板。
 * 书籍量大的商家走这条链路，个人卖家仍用 /v1/listings 单件发布。
 */
@Service
public class MerchantService {

    private final SysMerchantMapper merchantMapper;
    private final SysUserMapper userMapper;
    private final BizListingMapper listingMapper;
    private final BizOrdersMapper ordersMapper;
    private final BizSettlementMapper settlementMapper;
    private final BizReviewMapper reviewMapper;
    private final NoticeService noticeService;

    public MerchantService(SysMerchantMapper merchantMapper, SysUserMapper userMapper,
                           BizListingMapper listingMapper, BizOrdersMapper ordersMapper,
                           BizSettlementMapper settlementMapper, BizReviewMapper reviewMapper,
                           NoticeService noticeService) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.listingMapper = listingMapper;
        this.ordersMapper = ordersMapper;
        this.settlementMapper = settlementMapper;
        this.reviewMapper = reviewMapper;
        this.noticeService = noticeService;
    }

    /**
     * 入驻申请：只有<b>商户账号</b>（商户登录创建，role=MERCHANT）能提交，提交后进入 PENDING 等运营审核。
     * 学生账号不能通过这里变成商户——商户是独立的账号类型，不再走"进阶码/学生升级"那条路。
     */
    @Transactional
    public SysMerchant apply(Long userId, String shopName, String licenseUrl, String contact) {
        if (shopName == null || shopName.isBlank()) {
            throw BizException.badRequest("请填写店铺名称");
        }
        SysUser applicant = userMapper.selectById(userId);
        if (applicant == null) {
            throw BizException.notFound("用户不存在");
        }
        if (!"MERCHANT".equals(applicant.getRole())) {
            throw BizException.forbidden("只有商户账号才能提交入驻资料，请退出后用「商户登录」进入");
        }
        SysMerchant exists = merchantMapper.selectOne(new LambdaQueryWrapper<SysMerchant>()
                .eq(SysMerchant::getUserId, userId).last("limit 1"));
        if (exists != null && "APPROVED".equals(exists.getStatus())) {
            throw BizException.conflict("你已入驻商户，无需重复申请");
        }
        SysMerchant m = exists == null ? new SysMerchant() : exists;
        m.setUserId(userId);
        m.setShopName(shopName.trim());
        m.setLicenseUrl(licenseUrl == null ? "" : licenseUrl);
        m.setContact(contact == null ? "" : contact);
        m.setStatus("PENDING");
        m.setRejectReason("");
        if (exists == null) {
            merchantMapper.insert(m);
        } else {
            merchantMapper.updateById(m);
        }
        return m;
    }

    public SysMerchant my(Long userId) {
        return merchantMapper.selectOne(new LambdaQueryWrapper<SysMerchant>()
                .eq(SysMerchant::getUserId, userId).last("limit 1"));
    }

    public List<SysMerchant> adminList(String status) {
        LambdaQueryWrapper<SysMerchant> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(SysMerchant::getStatus, status);
        }
        return merchantMapper.selectList(q.orderByDesc(SysMerchant::getId));
    }

    @Transactional
    public SysMerchant review(Long adminId, Long merchantId, boolean approved, String reason) {
        SysMerchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            throw BizException.notFound("商户不存在");
        }
        if (!"PENDING".equals(m.getStatus())) {
            throw BizException.conflict("该商户当前状态不可审核：" + m.getStatus());
        }
        m.setStatus(approved ? "APPROVED" : "REJECTED");
        m.setRejectReason(approved ? "" : (reason == null ? "资料不完整" : reason));
        m.setApprovedBy(adminId);
        m.setApprovedAt(LocalDateTime.now());
        merchantMapper.updateById(m);
        SysUser user = userMapper.selectById(m.getUserId());
        if (user != null && approved) {
            user.setRole("MERCHANT");
            user.setMerchantId(m.getId());
            userMapper.updateById(user);
            noticeService.push(user.getId(), "SYSTEM", "商户入驻已通过",
                    "店铺「" + m.getShopName() + "」已开通，可批量上下架图书", "MERCHANT", m.getId());
        } else if (user != null) {
            noticeService.push(user.getId(), "SYSTEM", "商户入驻未通过",
                    m.getRejectReason(), "MERCHANT", m.getId());
        }
        return m;
    }

    /** 批量发布：单次最多 100 条，任一校验失败整批回滚。 */
    @Transactional
    public Map<String, Object> batchPublish(Long userId, List<Draft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw BizException.badRequest("请至少提交一本书");
        }
        if (drafts.size() > 100) {
            throw BizException.badRequest("单次最多批量上传 100 本");
        }
        SysMerchant merchant = requireApproved(userId);
        List<Long> ids = new ArrayList<>();
        for (Draft d : drafts) {
            if (d.title() == null || d.title().isBlank()) {
                throw BizException.badRequest("书名不能为空");
            }
            if (d.price() == null || d.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw BizException.badRequest("价格必须大于 0：" + d.title());
            }
            BizListing l = new BizListing();
            l.setSellerId(userId);
            l.setMerchantId(merchant.getId());
            l.setTextbookId(d.textbookId() == null ? 0L : d.textbookId());
            l.setCourseId(d.courseId() == null ? 0L : d.courseId());
            l.setTitle(d.title().trim());
            l.setPrice(d.price());
            l.setOriginalPrice(d.originalPrice() == null ? BigDecimal.ZERO : d.originalPrice());
            l.setConditionLevel(d.conditionLevel() == null ? "GOOD" : d.conditionLevel());
            l.setDescription(d.description() == null ? "" : d.description());
            l.setCoverUrl(d.coverUrl() == null ? "" : d.coverUrl());
            l.setImageCount(d.imageCount() == null ? 0 : d.imageCount());
            l.setDorm(d.dorm() == null ? "商户仓库" : d.dorm());
            l.setStatus("ON_SALE");
            l.setViewCount(0);
            l.setFavoriteCount(0);
            l.setPublishedAt(LocalDateTime.now());
            listingMapper.insert(l);
            ids.add(l.getId());
        }
        merchant.setListingCount((int) countOnSale(merchant.getId()));
        merchantMapper.updateById(merchant);
        Map<String, Object> map = new HashMap<>();
        map.put("count", ids.size());
        map.put("listingIds", ids);
        return map;
    }

    @Transactional
    public Map<String, Object> batchOffline(Long userId, List<Long> listingIds) {
        requireApproved(userId);
        if (listingIds == null || listingIds.isEmpty()) {
            throw BizException.badRequest("请选择要下架的商品");
        }
        int changed = 0;
        for (Long id : listingIds) {
            BizListing l = listingMapper.selectById(id);
            if (l == null || !userId.equals(l.getSellerId())) {
                continue;
            }
            if (!"ON_SALE".equals(l.getStatus())) {
                continue;
            }
            l.setStatus("OFFLINE");
            listingMapper.updateById(l);
            changed++;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("count", changed);
        return map;
    }

    public Map<String, Object> stats(Long userId) {
        SysMerchant m = requireApproved(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("merchantId", m.getId());
        map.put("shopName", m.getShopName());
        map.put("onSale", countOnSale(m.getId()));
        map.put("total", listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, m.getId())));
        return map;
    }

    /** 商户商品分页：status 为空/all 表示全部，keyword 模糊匹配书名。 */
    public PageResult<Map<String, Object>> listings(Long userId, String status, String keyword,
                                                    long page, long size) {
        SysMerchant m = requireApproved(userId);
        LambdaQueryWrapper<BizListing> q = new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, m.getId());
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            q.eq(BizListing::getStatus, status.toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(BizListing::getTitle, keyword.trim());
        }
        q.orderByDesc(BizListing::getId);
        Page<BizListing> p = listingMapper.selectPage(new Page<>(page, size), q);
        return PageResult.of(p, this::listingView);
    }

    /** 单条上/下架：只允许自己店铺的商品，且状态必须能迁移。 */
    @Transactional
    public Map<String, Object> toggleListing(Long userId, Long listingId, boolean online) {
        SysMerchant m = requireApproved(userId);
        BizListing l = listingMapper.selectById(listingId);
        if (l == null || !m.getId().equals(l.getMerchantId())) {
            throw BizException.notFound("商品不存在或不属于你的店铺");
        }
        String from = online ? "OFFLINE" : "ON_SALE";
        if (!from.equals(l.getStatus())) {
            throw BizException.conflict(online ? "只有在已下架的商品才能重新上架" : "只有在售商品才能下架");
        }
        l.setStatus(online ? "ON_SALE" : "OFFLINE");
        listingMapper.updateById(l);
        m.setListingCount((int) countOnSale(m.getId()));
        merchantMapper.updateById(m);
        Map<String, Object> map = new HashMap<>();
        map.put("id", l.getId());
        map.put("status", l.getStatus());
        return map;
    }

    /** 批量改价：mode = SET 直接设价 / DELTA 加减金额 / PERCENT 按百分比（-10 表示降 10%）。 */
    @Transactional
    public Map<String, Object> batchPrice(Long userId, List<Long> listingIds, BigDecimal value, String mode) {
        SysMerchant m = requireApproved(userId);
        if (listingIds == null || listingIds.isEmpty()) {
            throw BizException.badRequest("请选择要改价的商品");
        }
        if (value == null) {
            throw BizException.badRequest("请填写价格");
        }
        String how = mode == null ? "SET" : mode.toUpperCase();
        int changed = 0;
        for (Long id : listingIds) {
            BizListing l = listingMapper.selectById(id);
            if (l == null || !m.getId().equals(l.getMerchantId()) || "SOLD".equals(l.getStatus())) {
                continue;
            }
            BigDecimal next = switch (how) {
                case "DELTA" -> l.getPrice().add(value);
                case "PERCENT" -> l.getPrice().multiply(BigDecimal.ONE.add(value.movePointLeft(2)));
                default -> value;
            };
            next = next.setScale(2, RoundingMode.HALF_UP);
            if (next.compareTo(new BigDecimal("0.01")) < 0) {
                throw BizException.badRequest("改价后价格不能低于 0.01：" + l.getTitle());
            }
            l.setPrice(next);
            listingMapper.updateById(l);
            changed++;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("count", changed);
        return map;
    }

    /** 商户订单分页：只统计本店铺商品产生的订单。 */
    public PageResult<Map<String, Object>> orders(Long userId, String status, long page, long size) {
        SysMerchant m = requireApproved(userId);
        LambdaQueryWrapper<BizOrders> q = new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getMerchantId, m.getId());
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            q.eq(BizOrders::getStatus, status.toUpperCase());
        }
        q.orderByDesc(BizOrders::getId);
        Page<BizOrders> p = ordersMapper.selectPage(new Page<>(page, size), q);
        List<BizOrders> records = p.getRecords();
        Map<Long, String> buyerNames = nickNames(records.stream().map(BizOrders::getBuyerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> titles = listingTitles(records.stream().map(BizOrders::getListingId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return PageResult.of(p, o -> orderView(o, buyerNames, titles));
    }

    /** 商户工作台：今日成交、待核销、待处理退款、在售/下架库存 + 近 7 日成交。 */
    public Map<String, Object> dashboard(Long userId) {
        SysMerchant m = requireApproved(userId);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        List<BizOrders> recent = ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getMerchantId, m.getId())
                .ge(BizOrders::getCreatedAt, weekStart));
        List<Map<String, Object>> series = new ArrayList<>();
        BigDecimal weekGmv = BigDecimal.ZERO;
        long weekOrders = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            BigDecimal gmv = BigDecimal.ZERO;
            long orders = 0;
            long verifies = 0;
            for (BizOrders o : recent) {
                if (o.getCreatedAt() == null || !d.equals(o.getCreatedAt().toLocalDate())) {
                    continue;
                }
                orders++;
                if ("COMPLETED".equals(o.getStatus())) {
                    gmv = gmv.add(o.getAmount() == null ? BigDecimal.ZERO : o.getAmount());
                    verifies++;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString().substring(5));
            row.put("gmv", gmv);
            row.put("orders", orders);
            row.put("verifies", verifies);
            series.add(row);
            weekGmv = weekGmv.add(gmv);
            weekOrders += orders;
        }
        long todayOrders = recent.stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(todayStart)).count();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("merchantId", m.getId());
        map.put("shopName", m.getShopName());
        map.put("onSale", countOnSale(m.getId()));
        map.put("offline", countByStatus(m.getId(), "OFFLINE"));
        map.put("total", listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, m.getId())));
        map.put("todayOrders", todayOrders);
        map.put("weekOrders", weekOrders);
        map.put("weekGmv", weekGmv);
        map.put("waitVerify", orderCount(m.getId(), "WAIT_VERIFY"));
        map.put("refunding", ordersMapper.selectCount(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getMerchantId, m.getId())
                .in(BizOrders::getStatus, "REFUNDING", "REFUND_AGREED", "ARBITRATING")));
        map.put("pendingPay", orderCount(m.getId(), "PENDING_PAY"));
        map.put("views", sumMetric(m.getId(), true));
        map.put("favorites", sumMetric(m.getId(), false));
        map.put("series", series);
        return map;
    }

    /**
     * 店铺主页（游客可看）：店铺资料 + 汇总指标 + 在售商品分页 + 最近评价。
     * 只暴露公开信息，联系方式按脱敏规则输出。
     */
    public Map<String, Object> shop(Long merchantId, long page, long size) {
        SysMerchant m = merchantMapper.selectById(merchantId);
        if (m == null || (m.getDeleted() != null && m.getDeleted() == 1) || !"APPROVED".equals(m.getStatus())) {
            throw BizException.notFound("店铺不存在或未通过审核");
        }
        long p = page <= 0 ? 1 : page;
        long s = size <= 0 ? 12 : Math.min(size, 50);

        Page<BizListing> lp = listingMapper.selectPage(new Page<>(p, s),
                new LambdaQueryWrapper<BizListing>()
                        .eq(BizListing::getMerchantId, merchantId)
                        .eq(BizListing::getStatus, "ON_SALE")
                        .orderByDesc(BizListing::getId));
        List<Map<String, Object>> listings = lp.getRecords().stream().map(this::listingView).toList();

        long sold = ordersMapper.selectCount(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getMerchantId, merchantId)
                .eq(BizOrders::getStatus, "COMPLETED"));
        long reviewCount = reviewMapper.selectCount(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, m.getUserId())
                .eq(BizReview::getStatus, "VISIBLE"));
        List<BizReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, m.getUserId())
                .eq(BizReview::getStatus, "VISIBLE")
                .orderByDesc(BizReview::getId)
                .last("LIMIT 5"));
        List<Map<String, Object>> reviewRows = new ArrayList<>();
        for (BizReview r : reviews) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("rating", r.getRating());
            row.put("content", r.getContent());
            row.put("createdAt", r.getCreatedAt());
            row.put("reviewer", Integer.valueOf(1).equals(r.getAnonymous()) ? "匿名同学" : nickNames(Set.of(r.getReviewerId())).get(r.getReviewerId()));
            reviewRows.add(row);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("onSale", countOnSale(merchantId));
        stats.put("sold", sold);
        stats.put("views", sumMetric(merchantId, true));
        stats.put("favorites", sumMetric(merchantId, false));
        stats.put("reviews", reviewCount);
        stats.put("avgRating", avgRating(m.getUserId(), reviewCount));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("merchantId", m.getId());
        map.put("shopName", m.getShopName());
        map.put("contact", maskContact(m.getContact()));
        map.put("since", m.getApprovedAt() != null ? m.getApprovedAt() : m.getCreatedAt());
        map.put("stats", stats);
        map.put("listings", listings);
        map.put("listingTotal", lp.getTotal());
        map.put("listingPage", p);
        map.put("listingSize", s);
        map.put("reviews", reviewRows);
        return map;
    }

    private String maskContact(String contact) {
        if (contact == null || contact.isBlank()) {
            return "";
        }
        String c = contact.trim();
        if (c.matches("^1\\d{10}$")) {
            return c.substring(0, 3) + "****" + c.substring(7);
        }
        return c;
    }

    /** 店铺全部商品的浏览量或收藏量之和 */
    private long sumMetric(Long merchantId, boolean views) {
        List<BizListing> all = listingMapper.selectList(new LambdaQueryWrapper<BizListing>()
                .select(views ? BizListing::getViewCount : BizListing::getFavoriteCount)
                .eq(BizListing::getMerchantId, merchantId));
        long sum = 0;
        for (BizListing l : all) {
            Integer v = views ? l.getViewCount() : l.getFavoriteCount();
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }

    private String avgRating(Long targetId, long reviewCount) {
        if (reviewCount == 0) {
            return "0.0";
        }
        List<BizReview> list = reviewMapper.selectList(new LambdaQueryWrapper<BizReview>()
                .select(BizReview::getRating)
                .eq(BizReview::getTargetId, targetId)
                .eq(BizReview::getStatus, "VISIBLE"));
        long sum = 0;
        long n = 0;
        for (BizReview r : list) {
            if (r.getRating() != null) {
                sum += r.getRating();
                n++;
            }
        }
        if (n == 0) {
            return "0.0";
        }
        return BigDecimal.valueOf(sum * 10 / n).divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP).toPlainString();
    }

    /** 结算流水：已完成订单的结算记录（成交金额与平台佣金）。 */
    public PageResult<Map<String, Object>> settlements(Long userId, long page, long size) {
        requireApproved(userId);
        Page<BizSettlement> p = settlementMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BizSettlement>()
                        .eq(BizSettlement::getSellerId, userId)
                        .orderByDesc(BizSettlement::getId));
        return PageResult.of(p, s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("orderNo", s.getOrderNo());
            row.put("amount", s.getAmount());
            row.put("commission", s.getCommission());
            row.put("status", s.getStatus());
            row.put("settleAt", s.getSettleAt());
            row.put("createdAt", s.getCreatedAt());
            return row;
        });
    }

    private Map<String, Object> listingView(BizListing l) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", l.getId());
        row.put("title", l.getTitle());
        row.put("price", l.getPrice());
        row.put("originalPrice", l.getOriginalPrice());
        row.put("conditionLevel", l.getConditionLevel());
        row.put("coverUrl", l.getCoverUrl());
        row.put("status", l.getStatus());
        row.put("viewCount", l.getViewCount());
        row.put("favoriteCount", l.getFavoriteCount());
        row.put("imageCount", l.getImageCount());
        row.put("dorm", l.getDorm());
        row.put("publishedAt", l.getPublishedAt());
        row.put("createdAt", l.getCreatedAt());
        return row;
    }

    private Map<String, Object> orderView(BizOrders o, Map<Long, String> buyerNames, Map<Long, String> titles) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", o.getOrderNo());
        row.put("listingId", o.getListingId());
        row.put("listingTitle", titles.getOrDefault(o.getListingId(), ""));
        row.put("buyerId", o.getBuyerId());
        row.put("buyerName", buyerNames.getOrDefault(o.getBuyerId(), ""));
        row.put("amount", o.getAmount());
        row.put("commission", o.getCommission());
        row.put("status", o.getStatus());
        row.put("payChannel", o.getPayChannel());
        row.put("meetPlace", o.getMeetPlace());
        row.put("meetTime", o.getMeetTime());
        row.put("signPhotoUrl", o.getSignPhotoUrl());
        row.put("paidAt", o.getPaidAt());
        row.put("verifiedAt", o.getVerifiedAt());
        row.put("completedAt", o.getCompletedAt());
        row.put("createdAt", o.getCreatedAt());
        return row;
    }

    private Map<Long, String> nickNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .filter(u -> u.getNickname() != null)
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
    }

    private Map<Long, String> listingTitles(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return listingMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(BizListing::getId, BizListing::getTitle, (a, b) -> a));
    }

    private long countByStatus(Long merchantId, String status) {
        Long n = listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, merchantId)
                .eq(BizListing::getStatus, status));
        return n == null ? 0 : n;
    }

    private long orderCount(Long merchantId, String status) {
        Long n = ordersMapper.selectCount(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getMerchantId, merchantId)
                .eq(BizOrders::getStatus, status));
        return n == null ? 0 : n;
    }

    private long countOnSale(Long merchantId) {
        Long n = listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, merchantId)
                .eq(BizListing::getStatus, "ON_SALE"));
        return n == null ? 0 : n;
    }

    private SysMerchant requireApproved(Long userId) {
        SysMerchant m = my(userId);
        if (m == null) {
            throw BizException.forbidden("请先申请入驻商户");
        }
        if (!"APPROVED".equals(m.getStatus())) {
            throw BizException.forbidden("商户尚未通过审核：" + m.getStatus());
        }
        return m;
    }

    /** 批量上传的单条图书信息。 */
    public record Draft(String title, Long textbookId, Long courseId, BigDecimal price, BigDecimal originalPrice,
                        String conditionLevel, String description, String coverUrl, Integer imageCount,
                        String dorm) {
    }
}
