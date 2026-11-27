package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizGroupBuy;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.GroupBuyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 班级接龙（C2B 反向聚合）。
 */
@RestController
@RequestMapping("/v1/group-buys")
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    public GroupBuyController(GroupBuyService groupBuyService) {
        this.groupBuyService = groupBuyService;
    }

    public record CreateReq(Long courseId, Long textbookId, String title, Integer targetCount,
                            BigDecimal unitPrice, LocalDateTime deadline) {
    }

    public record JoinReq(Integer quantity, String contact) {
    }

    @PostMapping
    public ApiResponse<BizGroupBuy> create(@RequestBody CreateReq req) {
        return ApiResponse.ok(groupBuyService.create(UserContext.requireUserId(), req.courseId(), req.textbookId(),
                req.title(), req.targetCount(), req.unitPrice(), req.deadline()));
    }

    @GetMapping
    public ApiResponse<PageResult<BizGroupBuy>> list(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(groupBuyService.list(status, page, size));
    }

    @GetMapping("/{inviteCode}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String inviteCode) {
        return ApiResponse.ok(groupBuyService.detail(inviteCode));
    }

    @PostMapping("/{inviteCode}/join")
    public ApiResponse<BizGroupBuy> join(@PathVariable String inviteCode, @RequestBody JoinReq req) {
        return ApiResponse.ok(groupBuyService.join(UserContext.requireUserId(), inviteCode, req.quantity(),
                req.contact()));
    }

    @PostMapping("/{inviteCode}/close")
    public ApiResponse<BizGroupBuy> close(@PathVariable String inviteCode) {
        return ApiResponse.ok(groupBuyService.close(UserContext.requireUserId(), inviteCode));
    }
}
