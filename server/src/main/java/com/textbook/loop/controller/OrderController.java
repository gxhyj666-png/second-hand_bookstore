package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 担保交易：下单 → 支付（资金冻结）→ 自提核销 → 结算。
 */
@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    public record CreateReq(Long listingId, Long pointId, String requestId) {
    }

    public record PayReq(String channel) {
    }

    public record RefundReq(String reason) {
    }

    public record VerifyReq(String orderNo, String code, String method) {
    }

    @PostMapping
    public ApiResponse<BizOrders> create(@RequestBody CreateReq req) {
        return ApiResponse.ok(orderService.create(UserContext.requireUserId(), req.listingId(), req.pointId(),
                req.requestId()));
    }

    @GetMapping
    public ApiResponse<PageResult<BizOrders>> list(@RequestParam(defaultValue = "buyer") String role,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(orderService.list(UserContext.requireUserId(), role, status, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<BizOrders> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.detail(orderNo, UserContext.requireUserId()));
    }

    @PostMapping("/{orderNo}/pay")
    public ApiResponse<BizOrders> pay(@PathVariable String orderNo, @RequestBody(required = false) PayReq req) {
        return ApiResponse.ok(orderService.pay(UserContext.requireUserId(), orderNo,
                req == null ? "MOCK" : req.channel()));
    }

    @PostMapping("/{orderNo}/wait-verify")
    public ApiResponse<BizOrders> waitVerify(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.waitVerify(UserContext.requireUserId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<BizOrders> cancel(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.cancel(UserContext.requireUserId(), orderNo));
    }

    @PostMapping("/{orderNo}/refund")
    public ApiResponse<BizOrders> refund(@PathVariable String orderNo, @RequestBody RefundReq req) {
        return ApiResponse.ok(orderService.refund(UserContext.requireUserId(), orderNo, req.reason()));
    }

    @GetMapping("/{orderNo}/verify-code")
    public ApiResponse<Map<String, Object>> verifyCode(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.verifyCode(orderNo, UserContext.requireUserId()));
    }

    /** 店员核销：operator 必须是 CLERK/ADMIN，且订单处于 WAIT_VERIFY。 */
    @PostMapping("/verify")
    public ApiResponse<BizOrders> verify(@RequestBody VerifyReq req) {
        return ApiResponse.ok(orderService.verify(UserContext.requireUserId(), req.orderNo(), req.code(),
                req.method() == null ? "SCAN" : req.method()));
    }
}
