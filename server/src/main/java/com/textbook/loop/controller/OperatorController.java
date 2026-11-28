package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizCommission;
import com.textbook.loop.domain.BizOfflineVerify;
import com.textbook.loop.domain.BizTicket;
import com.textbook.loop.security.LoginUser;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.OperatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营端：推广码、下线数据、提成明细、投诉处理、线下认证核实。
 */
@RestController
@RequestMapping("/v1")
public class OperatorController {

    private final OperatorService operatorService;

    public OperatorController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /** 推广码绑定：任何登录用户填写运营的推广码，建立上下线关系（只能绑一次）。 */
    @PostMapping("/promotion/bind")
    public ApiResponse<Map<String, Object>> bind(@RequestBody BindReq req) {
        return ApiResponse.ok(operatorService.bind(UserContext.requireUserId(), req.promoCode()));
    }

    @GetMapping("/operator/overview")
    public ApiResponse<Map<String, Object>> overview() {
        LoginUser user = requireOperator();
        return ApiResponse.ok(operatorService.overview(user.userId()));
    }

    @GetMapping("/operator/invitees")
    public ApiResponse<PageResult<Map<String, Object>>> invitees(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        LoginUser user = requireOperator();
        return ApiResponse.ok(operatorService.invitees(user.userId(), page, size));
    }

    @GetMapping("/operator/commissions")
    public ApiResponse<PageResult<BizCommission>> commissions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        LoginUser user = requireOperator();
        return ApiResponse.ok(operatorService.commissions(user.userId(), status, page, size));
    }

    @GetMapping("/operator/complaints")
    public ApiResponse<PageResult<BizTicket>> complaints(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        requireOperator();
        return ApiResponse.ok(operatorService.complaints(status, page, size));
    }

    @PostMapping("/operator/complaints/{id}/handle")
    public ApiResponse<BizTicket> handleComplaint(@PathVariable Long id, @RequestBody HandleComplaintReq req) {
        LoginUser user = requireOperator();
        return ApiResponse.ok(operatorService.handleComplaint(user.userId(), id, req.status(), req.result()));
    }

    /** 用户提交线下认证材料（运营线下核验）。 */
    @PostMapping("/verify/offline")
    public ApiResponse<BizOfflineVerify> applyOffline(@RequestBody OfflineApplyReq req) {
        return ApiResponse.ok(operatorService.applyOffline(UserContext.requireUserId(),
                req.verifyType(), req.materialUrl(), req.note()));
    }

    @GetMapping("/operator/offline-verifies")
    public ApiResponse<PageResult<BizOfflineVerify>> offlineVerifies(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        requireOperator();
        return ApiResponse.ok(operatorService.offlineVerifies(status, page, size));
    }

    @PostMapping("/operator/offline-verifies/{id}/review")
    public ApiResponse<BizOfflineVerify> reviewOffline(@PathVariable Long id,
                                                      @RequestBody OfflineReviewReq req) {
        LoginUser user = requireOperator();
        return ApiResponse.ok(operatorService.reviewOffline(user.userId(), id, req.passed(), req.remark()));
    }

    private LoginUser requireOperator() {
        UserContext.requireOperator();
        return UserContext.require();
    }

    public record BindReq(String promoCode) {
    }

    public record HandleComplaintReq(String status, String result) {
    }

    public record OfflineApplyReq(String verifyType, String materialUrl, String note) {
    }

    public record OfflineReviewReq(boolean passed, String remark) {
    }
}
