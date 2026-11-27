package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.*;
import com.textbook.loop.dto.CourseTextbookView;
import com.textbook.loop.mapper.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程—教材知识库与检索推荐。
 */
@Service
public class CatalogService {

    private final SysCourseMapper courseMapper;
    private final SysTextbookMapper textbookMapper;
    private final SysCourseTextbookMapper mappingMapper;
    private final BizListingMapper listingMapper;
    private final BizOrdersMapper ordersMapper;
    private final BizFavoriteMapper favoriteMapper;

    public CatalogService(SysCourseMapper courseMapper, SysTextbookMapper textbookMapper,
                          SysCourseTextbookMapper mappingMapper, BizListingMapper listingMapper,
                          BizOrdersMapper ordersMapper, BizFavoriteMapper favoriteMapper) {
        this.courseMapper = courseMapper;
        this.textbookMapper = textbookMapper;
        this.mappingMapper = mappingMapper;
        this.listingMapper = listingMapper;
        this.ordersMapper = ordersMapper;
        this.favoriteMapper = favoriteMapper;
    }

    public PageResult<SysCourse> courses(String keyword, long page, long size) {
        LambdaQueryWrapper<SysCourse> q = new LambdaQueryWrapper<SysCourse>()
                .eq(SysCourse::getStatus, "ENABLED")
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysCourse::getName, keyword)
                        .or().like(SysCourse::getCode, keyword)
                        .or().like(SysCourse::getTeacher, keyword))
                .orderByAsc(SysCourse::getId);
        return PageResult.of(courseMapper.selectPage(Page.of(page, size), q));
    }

    public SysCourse course(Long id) {
        SysCourse c = courseMapper.selectById(id);
        if (c == null) {
            throw BizException.notFound("课程不存在");
        }
        return c;
    }

    /**
     * 课程指定/参考教材：这是"版本歧义"治理的核心数据源。
     */
    public List<CourseTextbookView> courseTextbooks(Long courseId) {
        List<SysCourseTextbook> mappings = mappingMapper.selectList(new LambdaQueryWrapper<SysCourseTextbook>()
                .eq(SysCourseTextbook::getCourseId, courseId)
                .orderByAsc(SysCourseTextbook::getBindType)
                .orderByAsc(SysCourseTextbook::getId));
        if (mappings.isEmpty()) {
            return List.of();
        }
        Map<Long, SysTextbook> books = textbookMapper.selectBatchIds(
                        mappings.stream().map(SysCourseTextbook::getTextbookId).toList()).stream()
                .collect(Collectors.toMap(SysTextbook::getId, b -> b));
        return mappings.stream().map(m -> {
            SysTextbook b = books.get(m.getTextbookId());
            if (b == null) {
                return null;
            }
            return new CourseTextbookView(m.getId(), m.getCourseId(), b.getId(), b.getTitle(), b.getAuthor(),
                    b.getPublisher(), b.getEdition(), b.getIsbn13(), b.getPrice(), b.getCoverUrl(),
                    m.getBindType(), m.getTeacher(), m.getSemester(), m.getSource());
        }).filter(Objects::nonNull).toList();
    }

    public PageResult<SysTextbook> textbooks(String keyword, String isbn, long page, long size) {
        LambdaQueryWrapper<SysTextbook> q = new LambdaQueryWrapper<SysTextbook>()
                .eq(SysTextbook::getStatus, "ENABLED")
                .eq(isbn != null && !isbn.isBlank(), SysTextbook::getIsbn13, isbn)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysTextbook::getTitle, keyword)
                        .or().like(SysTextbook::getAuthor, keyword)
                        .or().like(SysTextbook::getPublisher, keyword))
                .orderByAsc(SysTextbook::getId);
        return PageResult.of(textbookMapper.selectPage(Page.of(page, size), q));
    }

    public SysTextbook textbook(Long id) {
        SysTextbook b = textbookMapper.selectById(id);
        if (b == null) {
            throw BizException.notFound("教材不存在");
        }
        return b;
    }

    /**
     * 定价参考：取当前在售与该教材历史成交价，给出 min / max / avg / 样本数与建议价（均价 8 折）。
     */
    public Map<String, Object> priceIndex(Long textbookId) {
        List<BigDecimal> prices = new ArrayList<>();
        listingMapper.selectList(new LambdaQueryWrapper<BizListing>()
                        .eq(BizListing::getTextbookId, textbookId)
                        .in(BizListing::getStatus, "ON_SALE", "LOCKED", "SOLD")
                        .orderByDesc(BizListing::getId)
                        .last("limit 200"))
                .forEach(l -> {
                    if (l.getPrice() != null) {
                        prices.add(l.getPrice());
                    }
                });

        Map<String, Object> result = new LinkedHashMap<>();
        if (prices.isEmpty()) {
            SysTextbook b = textbook(textbookId);
            BigDecimal list = b.getPrice() == null ? BigDecimal.ZERO : b.getPrice();
            result.put("min", list.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP));
            result.put("max", list.multiply(new BigDecimal("0.60")).setScale(2, RoundingMode.HALF_UP));
            result.put("avg", list.multiply(new BigDecimal("0.45")).setScale(2, RoundingMode.HALF_UP));
            result.put("count", 0);
            result.put("suggest", list.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP));
            result.put("basis", "LIST_PRICE");
            return result;
        }
        BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(new BigDecimal(prices.size()), 2, RoundingMode.HALF_UP);
        result.put("min", min);
        result.put("max", max);
        result.put("avg", avg);
        result.put("count", prices.size());
        result.put("suggest", avg.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.HALF_UP));
        result.put("basis", "MARKET");
        return result;
    }

    private Long bookIdOrThrow(Long id) {
        if (textbookMapper.selectById(id) == null) {
            throw BizException.notFound("教材不存在");
        }
        return id;
    }

    /**
     * 推荐：
     * 1) 已登录：按"收藏/买过的教材所在课程"召回同课程在售商品（ItemCF 的简化版，冷启动直接用课程热度）；
     * 2) 未登录或召回为空：按浏览量取热门在售商品。
     */
    public List<BizListing> recommend(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), 30);
        List<Long> courseIds = new ArrayList<>();
        if (userId != null) {
            List<Long> listingIds = favoriteMapper.selectList(new LambdaQueryWrapper<BizFavorite>()
                            .eq(BizFavorite::getUserId, userId).last("limit 100"))
                    .stream().map(BizFavorite::getListingId).toList();
            if (!listingIds.isEmpty()) {
                listingMapper.selectBatchIds(listingIds).forEach(l -> {
                    if (l.getCourseId() != null) {
                        courseIds.add(l.getCourseId());
                    }
                });
            }
            ordersMapper.selectList(new LambdaQueryWrapper<BizOrders>()
                            .eq(BizOrders::getBuyerId, userId).last("limit 100"))
                    .forEach(o -> {
                        BizListing l = listingMapper.selectById(o.getListingId());
                        if (l != null && l.getCourseId() != null) {
                            courseIds.add(l.getCourseId());
                        }
                    });
        }
        List<Long> distinctCourses = courseIds.stream().distinct().toList();
        if (!distinctCourses.isEmpty()) {
            List<BizListing> byCourse = listingMapper.selectList(new LambdaQueryWrapper<BizListing>()
                    .eq(BizListing::getStatus, "ON_SALE")
                    .in(BizListing::getCourseId, distinctCourses)
                    .ne(userId != null, BizListing::getSellerId, userId)
                    .orderByDesc(BizListing::getViewCount)
                    .orderByDesc(BizListing::getPublishedAt)
                    .last("limit " + size));
            if (!byCourse.isEmpty()) {
                return byCourse;
            }
        }
        return listingMapper.selectList(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getStatus, "ON_SALE")
                .ne(userId != null, BizListing::getSellerId, userId)
                .orderByDesc(BizListing::getViewCount)
                .orderByDesc(BizListing::getPublishedAt)
                .last("limit " + size));
    }
}
