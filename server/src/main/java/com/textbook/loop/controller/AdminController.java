package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizGroupBuy;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizReview;
import com.textbook.loop.domain.BizTicket;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.AdminService;
import com.textbook.loop.service.TicketService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营后台（管理员/店员）。所有方法第一行做权限校验，
 * 这样即使拦截器只解析 token，也不会出现越权访问后台接口。
 */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final TicketService ticketService;

    public AdminController(AdminService adminService, TicketService ticketService) {
        this.adminService = adminService;
        this.ticketService = ticketService;
    }

    public record RejectReq(String reason) {
    }

    public record VerifyStudentReq(Boolean approved, String reason) {
    }

    public record ResolveReq(String status, String result) {
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<SysUser>> users(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String role,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.users(keyword, role, status, page, size));
    }

    @PostMapping("/users/{id}/verify")
    public ApiResponse<Void> verifyStudent(@PathVariable Long id, @RequestBody VerifyStudentReq req) {
        UserContext.requireAdmin();
        adminService.verifyStudent(UserContext.requireUserId(), id,
                req.approved() == null || req.approved(), req.reason());
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/freeze")
    public ApiResponse<Void> freeze(@PathVariable Long id) {
        UserContext.requireAdmin();
        adminService.freeze(UserContext.requireUserId(), id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/unfreeze")
    public ApiResponse<Void> unfreeze(@PathVariable Long id) {
        UserContext.requireAdmin();
        adminService.freeze(UserContext.requireUserId(), id, false);
        return ApiResponse.ok();
    }

    @GetMapping("/listings")
    public ApiResponse<PageResult<BizListing>> listings(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.listings(status, keyword, page, size));
    }

    @PostMapping("/listings/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        UserContext.requireAdmin();
        adminService.approveListing(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/listings/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id, @RequestBody(required = false) RejectReq req) {
        UserContext.requireAdmin();
        adminService.rejectListing(UserContext.requireUserId(), id, req == null ? null : req.reason());
        return ApiResponse.ok();
    }

    @GetMapping("/orders")
    public ApiResponse<PageResult<BizOrders>> orders(@RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.orders(status, page, size));
    }

    @GetMapping("/textbooks")
    public ApiResponse<PageResult<SysTextbook>> textbooks(@RequestParam(required = false) String keyword,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.textbooks(keyword, page, size));
    }

    @PostMapping("/textbooks")
    public ApiResponse<SysTextbook> saveTextbook(@RequestBody SysTextbook book) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.saveTextbook(UserContext.requireUserId(), book));
    }

    @DeleteMapping("/textbooks/{id}")
    public ApiResponse<Void> deleteTextbook(@PathVariable Long id) {
        UserContext.requireAdmin();
        adminService.deleteTextbook(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/tickets")
    public ApiResponse<PageResult<BizTicket>> tickets(@RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(ticketService.listAll(status, page, size));
    }

    @PostMapping("/tickets/{id}/resolve")
    public ApiResponse<BizTicket> resolve(@PathVariable Long id, @RequestBody ResolveReq req) {
        UserContext.requireAdmin();
        return ApiResponse.ok(ticketService.resolve(UserContext.requireUserId(), id, req.status(), req.result()));
    }

    @GetMapping("/group-buys")
    public ApiResponse<PageResult<BizGroupBuy>> groupBuys(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.groupBuys(status, page, size));
    }

    @GetMapping("/reviews")
    public ApiResponse<PageResult<BizReview>> reviews(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        UserContext.requireAdmin();
        return ApiResponse.ok(adminService.reviews(page, size));
    }

    @PostMapping("/reviews/{id}/hide")
    public ApiResponse<Void> hideReview(@PathVariable Long id) {
        UserContext.requireAdmin();
        adminService.hideReview(UserContext.requireUserId(), id);
        return ApiResponse.ok();
    }
}
