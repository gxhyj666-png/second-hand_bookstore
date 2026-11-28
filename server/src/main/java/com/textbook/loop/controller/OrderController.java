package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizMeet;
import com.textbook.loop.domain.BizOrders;
import com.textbook.loop.domain.BizRefund;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交易：下单 → 支付 → 约见 → 卖家核销 → 退货/介入 → 爽约上报。
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

    public record VerifyReq(String code, String signPhotoUrl) {
    }

    public record MeetReq(String type, String place, LocalDateTime time) {
    }

    public record MeetConfirmReq(boolean agreed, String note) {
    }

    public record RefundReq(String reason, String photoUrl, String refundType) {
    }

    public record RejectReq(String reason) {
    }

    public record NoteReq(String note) {
    }

    public record ArbitrateReq(boolean buyerWin, String note) {
    }

    @PostMapping
    public ApiResponse<BizOrders> create(@RequestBody CreateReq req) {
        return ApiResponse.ok(orderService.create(UserContext.requireUserId(), req.listingId(),
                req.pointId(), req.requestId()));
    }

    @GetMapping
    public ApiResponse<PageResult<BizOrders>> list(@RequestParam(defaultValue = "buyer") String role,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(orderService.list(UserContext.requireUserId(), role, status, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<BizOrders> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.detail(orderNo, UserContext.requireUserId()));
    }

    @PostMapping("/{orderNo}/pay")
    public ApiResponse<BizOrders> pay(@PathVariable String orderNo, @RequestBody(required = false) PayReq req) {
        return ApiResponse.ok(orderService.pay(UserContext.requireUserId(), orderNo,
                req == null ? null : req.channel()));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<BizOrders> cancel(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.cancel(UserContext.requireUserId(), orderNo));
    }

    /** 买家出示 6 位核销码（15 分钟有效，且不晚于约定时间后 15 分钟）。 */
    @GetMapping("/{orderNo}/verify-code")
    public ApiResponse<Map<String, Object>> verifyCode(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.verifyCode(orderNo, UserContext.requireUserId()));
    }

    /** 卖家重新生成核销码（约定时间过后 15 分钟内仍可重发，原码立即作废）。 */
    @PostMapping("/{orderNo}/verify-code/regenerate")
    public ApiResponse<Map<String, Object>> regenerateVerifyCode(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.regenerateVerifyCode(UserContext.requireUserId(), orderNo));
    }

    /** 卖家线下核销：核销码 + 签到照片。 */
    @PostMapping("/{orderNo}/verify")
    public ApiResponse<BizOrders> verify(@PathVariable String orderNo, @RequestBody VerifyReq req) {
        return ApiResponse.ok(orderService.verify(UserContext.requireUserId(), orderNo,
                req.code(), req.signPhotoUrl()));
    }

    // ---------------- 约见 ----------------

    @PostMapping("/{orderNo}/meets")
    public ApiResponse<BizMeet> proposeMeet(@PathVariable String orderNo, @RequestBody MeetReq req) {
        return ApiResponse.ok(orderService.proposeMeet(UserContext.requireUserId(), orderNo,
                req.type(), req.place(), req.time()));
    }

    @GetMapping("/{orderNo}/meets")
    public ApiResponse<?> meets(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.meets(orderNo, UserContext.requireUserId()));
    }

    @PostMapping("/meets/{meetId}/confirm")
    public ApiResponse<BizMeet> confirmMeet(@PathVariable Long meetId, @RequestBody MeetConfirmReq req) {
        return ApiResponse.ok(orderService.confirmMeet(UserContext.requireUserId(), meetId, req.agreed(), req.note()));
    }

    // ---------------- 退货 / 介入 ----------------

    @PostMapping("/{orderNo}/refund")
    public ApiResponse<BizRefund> applyRefund(@PathVariable String orderNo, @RequestBody RefundReq req) {
        return ApiResponse.ok(orderService.applyRefund(UserContext.requireUserId(), orderNo,
                req.reason(), req.photoUrl(), req.refundType()));
    }

    @PostMapping("/refunds/{refundId}/agree")
    public ApiResponse<BizRefund> agreeRefund(@PathVariable Long refundId) {
        return ApiResponse.ok(orderService.agreeRefund(UserContext.requireUserId(), refundId));
    }

    @PostMapping("/refunds/{refundId}/reject")
    public ApiResponse<BizRefund> rejectRefund(@PathVariable Long refundId, @RequestBody RejectReq req) {
        return ApiResponse.ok(orderService.rejectRefund(UserContext.requireUserId(), refundId, req.reason()));
    }

    @PostMapping("/refunds/{refundId}/finish")
    public ApiResponse<BizRefund> finishRefund(@PathVariable Long refundId) {
        return ApiResponse.ok(orderService.finishRefund(UserContext.requireUserId(), refundId));
    }

    @PostMapping("/refunds/{refundId}/arbitrate")
    public ApiResponse<BizRefund> requestArbitration(@PathVariable Long refundId, @RequestBody NoteReq req) {
        return ApiResponse.ok(orderService.requestArbitration(UserContext.requireUserId(), refundId, req.note()));
    }

    /** 平台判定（管理员）。 */
    @PostMapping("/refunds/{refundId}/judge")
    public ApiResponse<BizRefund> judge(@PathVariable Long refundId, @RequestBody ArbitrateReq req) {
        UserContext.requireAdmin();
        return ApiResponse.ok(orderService.arbitrate(UserContext.requireUserId(), refundId, req.buyerWin(), req.note()));
    }

    // ---------------- 爽约 ----------------

    @PostMapping("/{orderNo}/no-show")
    public ApiResponse<Map<String, Object>> noShow(@PathVariable String orderNo, @RequestBody NoteReq req) {
        return ApiResponse.ok(orderService.reportNoShow(UserContext.requireUserId(), orderNo, req.note()));
    }
}
