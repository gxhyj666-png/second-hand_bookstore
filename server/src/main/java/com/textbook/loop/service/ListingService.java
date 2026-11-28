package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.*;
import com.textbook.loop.dto.ListingDetailView;
import com.textbook.loop.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品发布、检索、收藏、埋点。
 */
@Service
public class ListingService {

    private static final List<String> CONDITIONS = List.of("NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR");

    private final BizListingMapper listingMapper;
    private final BizListingImageMapper imageMapper;
    private final BizFavoriteMapper favoriteMapper;
    private final BizTrackEventMapper trackMapper;
    private final SysTextbookMapper textbookMapper;
    private final SysCourseMapper courseMapper;
    private final SysUserMapper userMapper;

    public ListingService(BizListingMapper listingMapper, BizListingImageMapper imageMapper,
                          BizFavoriteMapper favoriteMapper, BizTrackEventMapper trackMapper,
                          SysTextbookMapper textbookMapper, SysCourseMapper courseMapper,
                          SysUserMapper userMapper) {
        this.listingMapper = listingMapper;
        this.imageMapper = imageMapper;
        this.favoriteMapper = favoriteMapper;
        this.trackMapper = trackMapper;
        this.textbookMapper = textbookMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
    }

    public BizListing publish(Long sellerId, Long textbookId, Long courseId, BigDecimal price, String condition,
                              String description, String dorm, List<String> images) {
        return publish(sellerId, textbookId, courseId, price, condition, description, dorm, images, "");
    }

    @Transactional
    public BizListing publish(Long sellerId, Long textbookId, Long courseId, BigDecimal price, String condition,
                              String description, String dorm, List<String> images, String videoUrl) {
        SysTextbook book = textbookMapper.selectById(textbookId);
        if (book == null) {
            throw BizException.notFound("教材不存在，请先选择教材");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("售价必须大于 0");
        }
        if (!CONDITIONS.contains(condition)) {
            throw BizException.badRequest("品相取值不合法：" + condition);
        }
        BizListing l = new BizListing();
        l.setSellerId(sellerId);
        l.setTextbookId(textbookId);
        l.setCourseId(courseId);
        l.setTitle(book.getTitle() + (book.getEdition() == null ? "" : " " + book.getEdition()));
        l.setPrice(price);
        l.setOriginalPrice(book.getPrice());
        l.setConditionLevel(condition);
        l.setDescription(description);
        l.setCoverUrl(images != null && !images.isEmpty() ? images.get(0) : book.getCoverUrl());
        l.setVideoUrl(videoUrl == null ? "" : videoUrl.trim());
        l.setImageCount(images == null ? 0 : images.size());
        l.setDorm(dorm);
        l.setStatus("ON_SALE");
        l.setViewCount(0);
        l.setFavoriteCount(0);
        l.setPublishedAt(LocalDateTime.now());
        listingMapper.insert(l);

        if (images != null) {
            int sort = 0;
            for (String url : images) {
                BizListingImage img = new BizListingImage();
                img.setListingId(l.getId());
                img.setUrl(url);
                img.setSortNo(sort++);
                img.setCreatedAt(LocalDateTime.now());
                imageMapper.insert(img);
            }
        }
        return l;
    }

    public PageResult<BizListing> search(Long currentUserId, Long textbookId, Long courseId, String keyword,
                                         String condition, String sort, long page, long size, Long sellerId) {
        LambdaQueryWrapper<BizListing> q = new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getStatus, "ON_SALE")
                .eq(textbookId != null, BizListing::getTextbookId, textbookId)
                .eq(courseId != null, BizListing::getCourseId, courseId)
                .eq(condition != null && !condition.isBlank(), BizListing::getConditionLevel, condition)
                .eq(sellerId != null, BizListing::getSellerId, sellerId)
                .like(keyword != null && !keyword.isBlank(), BizListing::getTitle, keyword)
                .ne(currentUserId != null, BizListing::getSellerId, currentUserId);
        applySort(q, sort);
        return PageResult.of(listingMapper.selectPage(Page.of(page, size), q));
    }

    private void applySort(LambdaQueryWrapper<BizListing> q, String sort) {
        String s = sort == null ? "relevance" : sort;
        switch (s) {
            case "price_asc" -> q.orderByAsc(BizListing::getPrice);
            case "price_desc" -> q.orderByDesc(BizListing::getPrice);
            case "newest" -> q.orderByDesc(BizListing::getPublishedAt);
            case "hot" -> q.orderByDesc(BizListing::getViewCount);
            default -> q.orderByDesc(BizListing::getPublishedAt);
        }
        q.orderByDesc(BizListing::getId);
    }

    public PageResult<BizListing> mine(Long sellerId, String status, long page, long size) {
        return PageResult.of(listingMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getSellerId, sellerId)
                .eq(status != null && !status.isBlank(), BizListing::getStatus, status)
                .orderByDesc(BizListing::getId)));
    }

    @Transactional
    public ListingDetailView detail(Long id, Long currentUserId) {
        BizListing l = listingMapper.selectById(id);
        if (l == null) {
            throw BizException.notFound("商品不存在或已下架");
        }
        // 浏览量 +1（演示用，真实系统应异步/计数服务）
        l.setViewCount((l.getViewCount() == null ? 0 : l.getViewCount()) + 1);
        listingMapper.updateById(l);

        SysTextbook book = textbookMapper.selectById(l.getTextbookId());
        SysCourse course = l.getCourseId() == null ? null : courseMapper.selectById(l.getCourseId());
        SysUser seller = userMapper.selectById(l.getSellerId());
        List<BizListingImage> images = imageMapper.selectList(new LambdaQueryWrapper<BizListingImage>()
                .eq(BizListingImage::getListingId, id).orderByAsc(BizListingImage::getSortNo));
        boolean favored = currentUserId != null && favoriteMapper.selectCount(new LambdaQueryWrapper<BizFavorite>()
                .eq(BizFavorite::getUserId, currentUserId)
                .eq(BizFavorite::getListingId, id)) > 0;
        return new ListingDetailView(l, book, course, ListingDetailView.SellerView.of(seller), images, favored);
    }

    @Transactional
    public BizListing update(Long userId, Long id, BigDecimal price, String description, String dorm, String videoUrl) {
        BizListing l = owned(userId, id);
        if (!"ON_SALE".equals(l.getStatus())) {
            throw BizException.conflict("仅可在售商品可编辑");
        }
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            l.setPrice(price);
        }
        if (description != null) {
            l.setDescription(description);
        }
        if (dorm != null) {
            l.setDorm(dorm);
        }
        if (videoUrl != null) {
            l.setVideoUrl(videoUrl.trim());
        }
        listingMapper.updateById(l);
        return l;
    }

    @Transactional
    public void offline(Long userId, Long id) {
        BizListing l = owned(userId, id);
        if (!"ON_SALE".equals(l.getStatus())) {
            throw BizException.conflict("当前状态不可下架");
        }
        l.setStatus("OFFLINE");
        listingMapper.updateById(l);
    }

    private BizListing owned(Long userId, Long id) {
        BizListing l = listingMapper.selectById(id);
        if (l == null) {
            throw BizException.notFound("商品不存在");
        }
        if (!l.getSellerId().equals(userId)) {
            throw BizException.forbidden("只能操作自己的商品");
        }
        return l;
    }

    @Transactional
    public boolean toggleFavorite(Long userId, Long listingId) {
        BizListing l = listingMapper.selectById(listingId);
        if (l == null) {
            throw BizException.notFound("商品不存在");
        }
        BizFavorite exist = favoriteMapper.selectOne(new LambdaQueryWrapper<BizFavorite>()
                .eq(BizFavorite::getUserId, userId)
                .eq(BizFavorite::getListingId, listingId)
                .last("limit 1"));
        int count = l.getFavoriteCount() == null ? 0 : l.getFavoriteCount();
        if (exist != null) {
            favoriteMapper.deleteById(exist.getId());
            l.setFavoriteCount(Math.max(0, count - 1));
            listingMapper.updateById(l);
            return false;
        }
        BizFavorite f = new BizFavorite();
        f.setUserId(userId);
        f.setListingId(listingId);
        f.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(f);
        l.setFavoriteCount(count + 1);
        listingMapper.updateById(l);
        return true;
    }

    public List<BizListing> favorites(Long userId) {
        List<Long> ids = favoriteMapper.selectList(new LambdaQueryWrapper<BizFavorite>()
                        .eq(BizFavorite::getUserId, userId).orderByDesc(BizFavorite::getId).last("limit 100"))
                .stream().map(BizFavorite::getListingId).toList();
        return ids.isEmpty() ? List.of() : listingMapper.selectBatchIds(ids);
    }

    public void track(Long userId, String event, String refType, Long refId, String page, String sessionId) {
        BizTrackEvent e = new BizTrackEvent();
        e.setUserId(userId);
        e.setEvent(event);
        e.setRefType(refType);
        e.setRefId(refId);
        e.setPage(page);
        e.setSessionId(sessionId);
        e.setCreatedAt(LocalDateTime.now());
        trackMapper.insert(e);
    }
}
