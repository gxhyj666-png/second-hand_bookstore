package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizCreditLog;
import com.textbook.loop.domain.BizReview;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.SocialService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评价与信用分。
 */
@RestController
@RequestMapping("/v1")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    public record ReviewReq(String orderNo, Integer rating, String content, Boolean anonymous) {
    }

    @PostMapping("/reviews")
    public ApiResponse<BizReview> create(@RequestBody ReviewReq req) {
        return ApiResponse.ok(socialService.create(UserContext.requireUserId(), req.orderNo(), req.rating(),
                req.content(), req.anonymous()));
    }

    @GetMapping("/users/{id}/reviews")
    public ApiResponse<PageResult<Map<String, Object>>> reviewsOf(@PathVariable Long id,
                                                                  @RequestParam(defaultValue = "1") long page,
                                                                  @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(socialService.reviewsOf(id, page, size));
    }

    @GetMapping("/users/{id}/credit-logs")
    public ApiResponse<PageResult<BizCreditLog>> creditLogs(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(socialService.creditLogs(id, page, size));
    }

    @GetMapping("/me/credit-logs")
    public ApiResponse<PageResult<BizCreditLog>> myCreditLogs(@RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(socialService.creditLogs(UserContext.requireUserId(), page, size));
    }
}
