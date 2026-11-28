package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.dto.ListingDetailView;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.ListingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品发布、检索、收藏、埋点。
 */
@RestController
@RequestMapping("/v1")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    public record PublishReq(Long textbookId, Long courseId, BigDecimal price, String condition,
                             String description, String dorm, List<String> images, String videoUrl) {
    }

    public record UpdateReq(BigDecimal price, String description, String dorm, String videoUrl) {
    }

    public record TrackReq(String event, String refType, Long refId, String page, String sessionId) {
    }

    @PostMapping("/listings")
    public ApiResponse<BizListing> publish(@RequestBody PublishReq req) {
        return ApiResponse.ok(listingService.publish(UserContext.requireUserId(), req.textbookId(), req.courseId(),
                req.price(), req.condition(), req.description(), req.dorm(), req.images(), req.videoUrl()));
    }

    @GetMapping("/listings")
    public ApiResponse<PageResult<BizListing>> search(@RequestParam(required = false) Long textbookId,
                                                      @RequestParam(required = false) Long courseId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String condition,
                                                      @RequestParam(required = false) String sort,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size,
                                                      @RequestParam(required = false) Long sellerId) {
        return ApiResponse.ok(listingService.search(UserContext.currentUserIdOrNull(), textbookId, courseId,
                keyword, condition, sort, page, size, sellerId));
    }

    @GetMapping("/listings/mine")
    public ApiResponse<PageResult<BizListing>> mine(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(listingService.mine(UserContext.requireUserId(), status, page, size));
    }

    @GetMapping("/listings/{id}")
    public ApiResponse<ListingDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(listingService.detail(id, UserContext.currentUserIdOrNull()));
    }

    @PutMapping("/listings/{id}")
    public ApiResponse<BizListing> update(@PathVariable Long id, @RequestBody UpdateReq req) {
        return ApiResponse.ok(listingService.update(UserContext.requireUserId(), id, req.price(),
                req.description(), req.dorm(), req.videoUrl()));
    }

    @PostMapping("/listings/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        listingService.offline(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/listings/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@PathVariable Long id) {
        boolean favored = listingService.toggleFavorite(UserContext.requireUserId(), id);
        return ApiResponse.ok(Map.of("favored", favored));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<BizListing>> favorites() {
        return ApiResponse.ok(listingService.favorites(UserContext.requireUserId()));
    }

    @PostMapping("/track")
    public ApiResponse<Void> track(@RequestBody TrackReq req) {
        listingService.track(UserContext.currentUserIdOrNull(), req.event(), req.refType(), req.refId(),
                req.page(), req.sessionId());
        return ApiResponse.ok();
    }
}
