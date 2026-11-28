package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizGroupBuy;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizReview;
import com.textbook.loop.domain.BizTicket;
import com.textbook.loop.domain.StatDaily;
import com.textbook.loop.domain.SysAdminLog;
import com.textbook.loop.domain.SysCourse;
import com.textbook.loop.domain.SysCourseTextbook;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizGroupBuyMapper;
import com.textbook.loop.mapper.BizListingMapper;
import com.textbook.loop.mapper.BizOrdersMapper;
import com.textbook.loop.mapper.BizReviewMapper;
import com.textbook.loop.mapper.BizTicketMapper;
import com.textbook.loop.mapper.BizTrackEventMapper;
import com.textbook.loop.mapper.StatDailyMapper;
import com.textbook.loop.mapper.SysAdminLogMapper;
import com.textbook.loop.mapper.SysCourseMapper;
import com.textbook.loop.mapper.SysCourseTextbookMapper;
import com.textbook.loop.mapper.SysTextbookMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营后台：看板、用户、教材、商品审核、订单、接龙。
 */
@Service
public class AdminService {

    private final SysUserMapper userMapper;
    private final BizListingMapper listingMapper;
    private final BizOrdersMapper ordersMapper;
    private final SysTextbookMapper textbookMapper;
    private final BizTicketMapper ticketMapper;
    private final BizGroupBuyMapper groupBuyMapper;
    private final BizReviewMapper reviewMapper;
    private final BizTrackEventMapper trackMapper;
    private final StatDailyMapper statMapper;
    private final SysAdminLogMapper adminLogMapper;
    private final SysCourseTextbookMapper courseTextbookMapper;
    private final SysCourseMapper courseMapper;

    public AdminService(SysUserMapper userMapper, BizListingMapper listingMapper, BizOrdersMapper ordersMapper,
                        SysTextbookMapper textbookMapper, BizTicketMapper ticketMapper,
                        BizGroupBuyMapper groupBuyMapper, BizReviewMapper reviewMapper,
                        BizTrackEventMapper trackMapper, StatDailyMapper statMapper,
                        SysAdminLogMapper adminLogMapper, SysCourseTextbookMapper courseTextbookMapper,
                        SysCourseMapper courseMapper) {
        this.userMapper = userMapper;
        this.listingMapper = listingMapper;
        this.ordersMapper = ordersMapper;
        this.textbookMapper = textbookMapper;
        this.ticketMapper = ticketMapper;
        this.groupBuyMapper = groupBuyMapper;
        this.reviewMapper = reviewMapper;
        this.trackMapper = trackMapper;
        this.statMapper = statMapper;
        this.adminLogMapper = adminLogMapper;
        this.courseTextbookMapper = courseTextbookMapper;
        this.courseMapper = courseMapper;
    }

    public Map<String, Object> dashboard() {
        long userCount = userMapper.selectCount(null);
        long listingCount = listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .in(BizListing::getStatus, "ON_SALE", "LOCKED"));
        long orderCount = ordersMapper.selectCount(null);
        long pendingTickets = ticketMapper.selectCount(new LambdaQueryWrapper<BizTicket>()
                .in(BizTicket::getStatus, "PENDING", "PROCESSING"));
        long openGroups = groupBuyMapper.selectCount(new LambdaQueryWrapper<BizGroupBuy>()
                .eq(BizGroupBuy::getStatus, "OPEN"));
        long pendingListings = listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getStatus, "PENDING"));

        List<BizOrders> settled = ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                .in(BizOrders::getStatus, "COMPLETED"));
        BigDecimal gmv = settled.stream()
                .map(o -> o.getAmount() == null ? BigDecimal.ZERO : o.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", userCount);
        m.put("onSaleCount", listingCount);
        m.put("orderCount", orderCount);
        m.put("gmv", gmv);
        m.put("pendingTickets", pendingTickets);
        m.put("openGroups", openGroups);
        m.put("pendingListings", pendingListings);
        m.put("series", series());
        m.put("funnel", funnel());
        m.put("conditionDist", conditionDist());
        return m;
    }

    private List<Map<String, Object>> series() {
        List<StatDaily> rows = statMapper.selectList(new LambdaQueryWrapper<StatDaily>()
                .ge(StatDaily::getStatDate, LocalDate.now().minusDays(6))
                .orderByAsc(StatDaily::getStatDate));
        List<Map<String, Object>> list = new ArrayList<>();
        for (StatDaily r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getStatDate() == null ? "" : r.getStatDate().toString().substring(5));
            m.put("gmv", r.getGmv());
            m.put("orders", r.getOrderCount());
            m.put("verifies", r.getVerifyCount());
            m.put("listings", r.getListingCount());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> funnel() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<String> events = List.of("VIEW_LISTING", "DETAIL", "PUBLISH", "CREATE_ORDER", "PAY", "VERIFY");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String e : events) {
            long c = trackMapper.selectCount(new LambdaQueryWrapper<com.textbook.loop.domain.BizTrackEvent>()
                    .eq(com.textbook.loop.domain.BizTrackEvent::getEvent, e)
                    .ge(com.textbook.loop.domain.BizTrackEvent::getCreatedAt, since));
            list.add(Map.of("stage", e, "count", c));
        }
        return list;
    }

    private List<Map<String, Object>> conditionDist() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String c : List.of("NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR")) {
            long n = listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                    .eq(BizListing::getConditionLevel, c));
            list.add(Map.of("name", c, "value", n));
        }
        return list;
    }

    public PageResult<SysUser> users(String keyword, String role, String status, long page, long size) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
                .eq(role != null && !role.isBlank(), SysUser::getRole, role)
                .eq(status != null && !status.isBlank(), SysUser::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getNickname, keyword)
                        .or().like(SysUser::getPhone, keyword)
                        .or().like(SysUser::getStudentNo, keyword)
                        .or().like(SysUser::getRealName, keyword))
                .orderByDesc(SysUser::getId);
        return PageResult.of(userMapper.selectPage(Page.of(page, size), q));
    }

    @Transactional
    public void freeze(Long adminId, Long userId, boolean frozen) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw BizException.conflict("不能冻结管理员账号");
        }
        user.setStatus(frozen ? "FROZEN" : "NORMAL");
        userMapper.updateById(user);
        log(adminId, frozen ? "USER_FREEZE" : "USER_UNFREEZE", "USER:" + userId, user.getNickname());
    }

    @Transactional
    public void verifyStudent(Long adminId, Long userId, boolean approved, String reason) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (!"PENDING".equals(user.getVerifyState())) {
            throw BizException.conflict("该用户不在待审核状态：" + user.getVerifyState());
        }
        user.setVerifyState(approved ? "VERIFIED" : "REJECTED");
        userMapper.updateById(user);
        log(adminId, approved ? "USER_VERIFY_APPROVE" : "USER_VERIFY_REJECT", "USER:" + userId,
                reason == null ? "" : reason);
    }

    public PageResult<BizListing> listings(String status, String keyword, long page, long size) {        LambdaQueryWrapper<BizListing> q = new LambdaQueryWrapper<BizListing>()
                .eq(status != null && !status.isBlank(), BizListing::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), BizListing::getTitle, keyword)
                .orderByDesc(BizListing::getId);
        return PageResult.of(listingMapper.selectPage(Page.of(page, size), q));
    }

    @Transactional
    public void approveListing(Long adminId, Long listingId) {
        BizListing l = listingMapper.selectById(listingId);
        if (l == null) {
            throw BizException.notFound("商品不存在");
        }
        if (!"PENDING".equals(l.getStatus())) {
            throw BizException.conflict("只有待审核商品可以上架");
        }
        l.setStatus("ON_SALE");
        l.setPublishedAt(LocalDateTime.now());
        listingMapper.updateById(l);
        log(adminId, "LISTING_APPROVE", "LISTING:" + listingId, l.getTitle());
    }

    @Transactional
    public void rejectListing(Long adminId, Long listingId, String reason) {
        BizListing l = listingMapper.selectById(listingId);
        if (l == null) {
            throw BizException.notFound("商品不存在");
        }
        l.setStatus("REJECTED");
        l.setRejectReason(reason == null ? "不符合发布规范" : reason);
        listingMapper.updateById(l);
        log(adminId, "LISTING_REJECT", "LISTING:" + listingId, l.getRejectReason());
    }

    public PageResult<BizOrders> orders(String status, long page, long size) {
        return PageResult.of(ordersMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizOrders>()
                .eq(status != null && !status.isBlank(), BizOrders::getStatus, status)
                .orderByDesc(BizOrders::getId)));
    }

    /** 后台按订单号查详情：不受买卖双方归属限制（仲裁页需要）。 */
    public BizOrders orderDetail(String orderNo) {
        BizOrders order = ordersMapper.selectOne(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getOrderNo, orderNo).last("limit 1"));
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    public PageResult<SysTextbook> textbooks(String keyword, long page, long size) {
        return PageResult.of(textbookMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<SysTextbook>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysTextbook::getTitle, keyword)
                        .or().like(SysTextbook::getIsbn13, keyword)
                        .or().like(SysTextbook::getAuthor, keyword))
                .orderByDesc(SysTextbook::getId)));
    }

    @Transactional
    public SysTextbook saveTextbook(Long adminId, SysTextbook book) {
        if (book.getId() == null) {
            book.setStatus(book.getStatus() == null ? "ENABLED" : book.getStatus());
            textbookMapper.insert(book);
            log(adminId, "TEXTBOOK_CREATE", "TEXTBOOK:" + book.getId(), book.getTitle());
        } else {
            textbookMapper.updateById(book);
            log(adminId, "TEXTBOOK_UPDATE", "TEXTBOOK:" + book.getId(), book.getTitle());
        }
        return book;
    }

    @Transactional
    public void deleteTextbook(Long adminId, Long id) {
        textbookMapper.deleteById(id);
        log(adminId, "TEXTBOOK_DELETE", "TEXTBOOK:" + id, "");
    }

    /** 教材关联的课程（后台教材库展开行）。 */
    public List<Map<String, Object>> textbookCourses(Long textbookId) {
        List<SysCourseTextbook> binds = courseTextbookMapper.selectList(
                new LambdaQueryWrapper<SysCourseTextbook>()
                        .eq(SysCourseTextbook::getTextbookId, textbookId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysCourseTextbook bind : binds) {
            SysCourse course = courseMapper.selectById(bind.getCourseId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("courseId", bind.getCourseId());
            row.put("name", course == null ? "已删除课程" : course.getName());
            row.put("teacher", bind.getTeacher() != null && !bind.getTeacher().isBlank()
                    ? bind.getTeacher() : (course == null ? "" : course.getTeacher()));
            row.put("bindType", bind.getBindType());
            row.put("semester", bind.getSemester());
            rows.add(row);
        }
        return rows;
    }

    public PageResult<BizGroupBuy> groupBuys(String status, long page, long size) {
        return PageResult.of(groupBuyMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizGroupBuy>()
                .eq(status != null && !status.isBlank(), BizGroupBuy::getStatus, status)
                .orderByDesc(BizGroupBuy::getId)));
    }

    public PageResult<BizReview> reviews(long page, long size) {
        return PageResult.of(reviewMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<BizReview>().orderByDesc(BizReview::getId)));
    }

    @Transactional
    public void hideReview(Long adminId, Long reviewId) {
        BizReview r = reviewMapper.selectById(reviewId);
        if (r == null) {
            throw BizException.notFound("评价不存在");
        }
        r.setStatus("HIDDEN");
        reviewMapper.updateById(r);
        log(adminId, "REVIEW_HIDE", "REVIEW:" + reviewId, "");
    }

    private void log(Long adminId, String action, String target, String detail) {
        SysAdminLog log = new SysAdminLog();
        log.setAdminId(adminId);
        log.setAction(action);
        log.setTarget(target == null ? "" : target);
        log.setDetail(detail == null ? "" : detail);
        log.setIp("");
        log.setCreatedAt(LocalDateTime.now());
        adminLogMapper.insert(log);
    }
}
