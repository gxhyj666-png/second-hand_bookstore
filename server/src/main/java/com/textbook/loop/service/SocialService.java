package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizCreditLog;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizReview;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizCreditLogMapper;
import com.textbook.loop.mapper.BizOrdersMapper;
import com.textbook.loop.mapper.BizReviewMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 信用评价（双向评价 + 信用分流水）。
 */
@Service
public class SocialService {

    private final BizReviewMapper reviewMapper;
    private final BizCreditLogMapper creditLogMapper;
    private final BizOrdersMapper ordersMapper;
    private final SysUserMapper userMapper;
    private final NoticeService noticeService;

    public SocialService(BizReviewMapper reviewMapper, BizCreditLogMapper creditLogMapper,
                         BizOrdersMapper ordersMapper, SysUserMapper userMapper,
                         NoticeService noticeService) {
        this.reviewMapper = reviewMapper;
        this.creditLogMapper = creditLogMapper;
        this.ordersMapper = ordersMapper;
        this.userMapper = userMapper;
        this.noticeService = noticeService;
    }

    @Transactional
    public BizReview create(Long reviewerId, String orderNo, Integer rating, String content, Boolean anonymous) {
        if (rating == null || rating < 1 || rating > 5) {
            throw BizException.badRequest("评分需为 1~5 星");
        }
        BizOrders order = ordersMapper.selectOne(new LambdaQueryWrapper<BizOrders>()
                .eq(BizOrders::getOrderNo, orderNo).last("limit 1"));
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        boolean buyer = order.getBuyerId().equals(reviewerId);
        boolean seller = order.getSellerId().equals(reviewerId);
        if (!buyer && !seller) {
            throw BizException.forbidden("只能评价自己参与的订单");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw BizException.conflict("交易完成后才能评价");
        }
        Long target = buyer ? order.getSellerId() : order.getBuyerId();
        long dup = reviewMapper.selectCount(new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getOrderId, order.getId())
                .eq(BizReview::getReviewerId, reviewerId));
        if (dup > 0) {
            throw BizException.conflict("该订单你已评价过");
        }

        BizReview review = new BizReview();
        review.setOrderId(order.getId());
        review.setReviewerId(reviewerId);
        review.setTargetId(target);
        review.setReviewerRole(buyer ? "BUYER" : "SELLER");
        review.setRating(rating);
        review.setContent(content);
        review.setAnonymous(Boolean.TRUE.equals(anonymous) ? 1 : 0);
        review.setStatus("VISIBLE");
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);

        int delta = rating >= 4 ? 3 : (rating == 3 ? 0 : -3);
        if (delta != 0) {
            addCredit(target, delta, rating >= 4 ? "REVIEW_GOOD" : "REVIEW_BAD", "REVIEW", review.getId());
        }
        noticeService.push(target, "CREDIT", "你收到一条新评价",
                (Boolean.TRUE.equals(anonymous) ? "匿名同学" : "交易对方") + "给了你 " + rating + " 星。",
                "REVIEW", review.getId());
        return review;
    }

    private void addCredit(Long userId, int delta, String reason, String refType, Long refId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        int after = Math.max(0, (user.getCreditScore() == null ? 100 : user.getCreditScore()) + delta);
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

    /**
     * 某用户的评价列表（匿名评价隐藏昵称）。
     */
    public PageResult<Map<String, Object>> reviewsOf(Long targetId, long page, long size) {
        var result = reviewMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizReview>()
                .eq(BizReview::getTargetId, targetId)
                .eq(BizReview::getStatus, "VISIBLE")
                .orderByDesc(BizReview::getId));
        List<Map<String, Object>> records = result.getRecords().stream().map(r -> {
            SysUser reviewer = userMapper.selectById(r.getReviewerId());
            boolean anonymous = r.getAnonymous() != null && r.getAnonymous() == 1;
            return Map.<String, Object>of(
                    "id", r.getId(),
                    "rating", r.getRating(),
                    "content", r.getContent() == null ? "" : r.getContent(),
                    "reviewerName", anonymous || reviewer == null ? "匿名同学" : reviewer.getNickname(),
                    "createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
        }).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public PageResult<BizCreditLog> creditLogs(Long userId, long page, long size) {
        return PageResult.of(creditLogMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<BizCreditLog>()
                        .eq(BizCreditLog::getUserId, userId)
                        .orderByDesc(BizCreditLog::getId)));
    }
}
