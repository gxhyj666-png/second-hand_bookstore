package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizReview;
import com.textbook.loop.domain.SysMerchant;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizListingMapper;
import com.textbook.loop.mapper.BizOrdersMapper;
import com.textbook.loop.mapper.BizReviewMapper;
import com.textbook.loop.mapper.SysMerchantMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人中心：我的发布 / 我的评价 / 我的数据 / 他人主页。
 *
 * <p>只读为主，写操作（改资料、换绑手机号）留在 {@link AuthService}。</p>
 */
@Service
public class MeService {

    /** 列表 size 上限，避免一次把整张表拉回来。 */
    private static final long MAX_SIZE = 50;

    private final BizListingMapper listingMapper;
    private final BizReviewMapper reviewMapper;
    private final BizOrdersMapper ordersMapper;
    private final SysUserMapper userMapper;
    private final SysMerchantMapper merchantMapper;
    private final NoticeService noticeService;

    public MeService(BizListingMapper listingMapper, BizReviewMapper reviewMapper,
                     BizOrdersMapper ordersMapper, SysUserMapper userMapper,
                     SysMerchantMapper merchantMapper, NoticeService noticeService) {
        this.listingMapper = listingMapper;
        this.reviewMapper = reviewMapper;
        this.ordersMapper = ordersMapper;
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.noticeService = noticeService;
    }

    private static long safeSize(long size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static long safePage(long page) {
        return page <= 0 ? 1 : page;
    }

    /** 个人中心顶部数据：发布 / 在售 / 已售 / 买卖成交 / 收到与发出的评价 / 未读消息。 */
    public Map<String, Object> stats(Long userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("published", countListing(userId, null));
        map.put("onSale", countListing(userId, "ON_SALE"));
        map.put("locked", countListing(userId, "LOCKED"));
        map.put("offline", countListing(userId, "OFFLINE"));
        map.put("sold", countListing(userId, "SOLD"));
        map.put("bought", ordersMapper.selectCount(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getBuyerId, userId).eq(BizOrders::getStatus, "COMPLETED")));
        map.put("soldOrders", ordersMapper.selectCount(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getSellerId, userId).eq(BizOrders::getStatus, "COMPLETED")));
        map.put("reviewsReceived", reviewMapper.selectCount(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, userId).eq(BizReview::getStatus, "VISIBLE")));
        map.put("reviewsSent", reviewMapper.selectCount(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getReviewerId, userId)));
        map.put("unread", noticeService.unreadCount(userId));
        SysUser user = userMapper.selectById(userId);
        map.put("creditScore", user == null || user.getCreditScore() == null ? 100 : user.getCreditScore());
        return map;
    }

    private long countListing(Long userId, String status) {
        LambdaQueryWrapper<BizListing> w = new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getSellerId, userId);
        if (status != null) {
            w.eq(BizListing::getStatus, status);
        }
        return listingMapper.selectCount(w);
    }

    /**
     * 我的评价。
     *
     * @param type received=别人给我的；sent=我发给别人的
     */
    public PageResult<Map<String, Object>> myReviews(Long userId, String type, long page, long size) {
        boolean received = !"sent".equalsIgnoreCase(type);
        LambdaQueryWrapper<BizReview> w = new LambdaQueryWrapper<>();
        if (received) {
            w.eq(BizReview::getTargetId, userId).eq(BizReview::getStatus, "VISIBLE");
        } else {
            w.eq(BizReview::getReviewerId, userId);
        }
        w.orderByDesc(BizReview::getId);
        var result = reviewMapper.selectPage(Page.of(safePage(page), safeSize(size)), w);
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizReview r : result.getRecords()) {
            boolean anonymous = r.getAnonymous() != null && r.getAnonymous() == 1;
            Long counterpartId = received ? r.getReviewerId() : r.getTargetId();
            SysUser counterpart = counterpartId == null ? null : userMapper.selectById(counterpartId);
            BizOrders order = r.getOrderId() == null ? null : ordersMapper.selectById(r.getOrderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("rating", r.getRating());
            m.put("content", r.getContent() == null ? "" : r.getContent());
            m.put("role", r.getReviewerRole());
            m.put("anonymous", anonymous);
            m.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
            m.put("counterpartId", counterpartId);
            // 别人给我的匿名评价不暴露对方身份；我发出去的当然知道自己给了谁
            m.put("counterpartName", received && anonymous ? "匿名同学"
                    : (counterpart == null ? "已注销用户" : counterpart.getNickname()));
            m.put("orderNo", order == null ? "" : order.getOrderNo());
            m.put("orderTitle", order == null ? "" : listingTitle(order.getListingId()));
            records.add(m);
        }
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 他人主页：公开信息 + 交易统计 + 在售预览 + 最近评价。
     * 不返回手机号、身份证号、真实姓名等隐私字段。
     */
    public Map<String, Object> profile(Long targetId) {
        SysUser user = userMapper.selectById(targetId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar() == null ? "" : user.getAvatar());
        map.put("college", user.getCollege() == null ? "" : user.getCollege());
        map.put("major", user.getMajor() == null ? "" : user.getMajor());
        map.put("grade", user.getGrade() == null ? "" : user.getGrade());
        map.put("role", user.getRole());
        map.put("creditScore", user.getCreditScore() == null ? 100 : user.getCreditScore());
        map.put("idVerified", user.getIdVerified() != null && user.getIdVerified() == 1);
        map.put("studentVerified", user.getStudentVerified() != null && user.getStudentVerified() == 1);
        map.put("faceVerified", user.getFaceVerified() != null && user.getFaceVerified() == 1);
        map.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toLocalDate().toString());

        Long merchantId = user.getMerchantId();
        String shopName = "";
        if (merchantId != null && merchantId > 0) {
            SysMerchant merchant = merchantMapper.selectById(merchantId);
            if (merchant != null && "APPROVED".equals(merchant.getStatus())) {
                shopName = merchant.getShopName();
                map.put("merchantId", merchantId);
            }
        }
        map.put("shopName", shopName);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("onSale", countListing(targetId, "ON_SALE"));
        stats.put("sold", countListing(targetId, "SOLD"));
        long reviewCount = reviewMapper.selectCount(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, targetId).eq(BizReview::getStatus, "VISIBLE"));
        stats.put("reviews", reviewCount);
        stats.put("avgRating", avgRating(targetId, reviewCount));
        map.put("stats", stats);

        List<Map<String, Object>> listings = new ArrayList<>();
        var pageListing = listingMapper.selectPage(Page.of(1, 6), new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getSellerId, targetId)
                .eq(BizListing::getStatus, "ON_SALE")
                .orderByDesc(BizListing::getId));
        for (BizListing l : pageListing.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("title", l.getTitle());
            m.put("price", l.getPrice());
            m.put("coverUrl", l.getCoverUrl());
            m.put("conditionLevel", l.getConditionLevel());
            listings.add(m);
        }
        map.put("listings", listings);

        List<Map<String, Object>> reviews = new ArrayList<>();
        var pageReview = reviewMapper.selectPage(Page.of(1, 3), new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, targetId)
                .eq(BizReview::getStatus, "VISIBLE")
                .orderByDesc(BizReview::getId));
        for (BizReview r : pageReview.getRecords()) {
            boolean anonymous = r.getAnonymous() != null && r.getAnonymous() == 1;
            SysUser reviewer = r.getReviewerId() == null ? null : userMapper.selectById(r.getReviewerId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("rating", r.getRating());
            m.put("content", r.getContent() == null ? "" : r.getContent());
            m.put("reviewerName", anonymous || reviewer == null ? "匿名同学" : reviewer.getNickname());
            m.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
            reviews.add(m);
        }
        map.put("reviews", reviews);
        return map;
    }

    private String avgRating(Long targetId, long reviewCount) {
        if (reviewCount <= 0) {
            return "0.0";
        }
        List<BizReview> all = reviewMapper.selectList(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, targetId)
                .eq(BizReview::getStatus, "VISIBLE")
                .select(BizReview::getRating));
        double sum = 0;
        int n = 0;
        for (BizReview r : all) {
            if (r.getRating() != null) {
                sum += r.getRating();
                n++;
            }
        }
        if (n == 0) {
            return "0.0";
        }
        return BigDecimal.valueOf(sum / n).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private String listingTitle(Long listingId) {
        if (listingId == null) {
            return "";
        }
        BizListing l = listingMapper.selectById(listingId);
        return l == null ? "" : l.getTitle();
    }

    /** 资料页字典：学院 / 专业 / 年级。 */
    public Map<String, Object> profileOptions() {
        List<Map<String, Object>> colleges = new ArrayList<>();
        colleges.add(college("计算机科学与工程学院", "计算机科学与技术", "软件工程", "网络工程", "数据科学与大数据技术", "人工智能"));
        colleges.add(college("电子信息与自动化学院", "电子信息工程", "自动化", "通信工程"));
        colleges.add(college("机电工程学院", "机械设计制造及其自动化", "材料成型及控制工程"));
        colleges.add(college("管理学院", "市场营销", "会计学", "物流管理"));
        colleges.add(college("外国语学院", "英语", "商务英语"));
        colleges.add(college("艺术设计学院", "视觉传达设计", "产品设计"));
        colleges.add(college("航空旅游学院", "飞行器制造工程", "旅游管理"));
        List<String> grades = List.of("2023级", "2024级", "2025级", "2026级");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("colleges", colleges);
        map.put("grades", grades);
        map.put("conditions", List.of(
                Map.of("value", "NEW", "label", "全新"),
                Map.of("value", "LIKE_NEW", "label", "几乎全新"),
                Map.of("value", "GOOD", "label", "良好"),
                Map.of("value", "FAIR", "label", "一般"),
                Map.of("value", "POOR", "label", "较差")));
        map.put("updatedAt", LocalDateTime.now().toLocalDate().toString());
        return map;
    }

    private Map<String, Object> college(String name, String... majors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("majors", List.of(majors));
        return m;
    }
}
