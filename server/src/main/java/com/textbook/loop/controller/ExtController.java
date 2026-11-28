package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.IsbnScanService;
import com.textbook.loop.service.IsbnService;
import com.textbook.loop.service.MerchantService;
import com.textbook.loop.service.SmsService;
import com.textbook.loop.service.IdentityService;
import com.textbook.loop.service.AuthService;
import com.textbook.loop.dto.TokenView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 认证与外部能力：短信验证码登录、实名/学生/人脸认证、ISBN 查询、商户端。
 *
 * <p>拆成独立 controller 是为了让 AuthController 保持「账号密码登录」的向后兼容，
 * 新能力都走 /v1/auth/sms-*、/v1/verify/*、/v1/merchant/*。</p>
 */
@RestController
@RequestMapping("/v1")
public class ExtController {

    private final SmsService smsService;
    private final AuthService authService;
    private final IdentityService identityService;
    private final IsbnService isbnService;
    private final IsbnScanService isbnScanService;
    private final MerchantService merchantService;

    public ExtController(SmsService smsService, AuthService authService, IdentityService identityService,
                         IsbnService isbnService, IsbnScanService isbnScanService, MerchantService merchantService) {
        this.smsService = smsService;
        this.authService = authService;
        this.identityService = identityService;
        this.isbnService = isbnService;
        this.isbnScanService = isbnScanService;
        this.merchantService = merchantService;
    }

    /**
     * @param purpose 验证码用途：LOGIN（默认）/ BIND_PHONE（换绑手机号）
     */
    public record SmsCodeReq(String phone, String purpose) {
    }

    public record SmsLoginReq(String phone, String code, String loginType) {
    }

    public record IdCardReq(String realName, String idNo, String frontUrl, String backUrl) {
    }

    public record StudentReq(String schoolName, String studentNo, String coverUrl, String infoUrl) {
    }

    public record FaceReq(String imageUrl) {
    }

    // ---------------- 短信验证码登录 ----------------

    @PostMapping("/auth/sms-code")
    public ApiResponse<Map<String, Object>> sendSmsCode(@RequestBody SmsCodeReq req) {
        String purpose = req.purpose() == null || req.purpose().isBlank()
                ? "LOGIN" : req.purpose().trim().toUpperCase();
        String code = smsService.send(req.phone(), purpose);
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("sent", true);
        map.put("purpose", purpose);
        map.put("provider", smsService.providerName());
        // 只有 mock 通道才回传验证码（方便演示与自动化）；接 Spug/真实短信后为 null
        map.put("devCode", code);
        return ApiResponse.ok(map);
    }

    @PostMapping("/auth/sms-login")
    public ApiResponse<TokenView> smsLogin(@RequestBody SmsLoginReq req) {
        return ApiResponse.ok(authService.smsLogin(req.phone(), req.code(), req.loginType()));
    }

    // ---------------- 实名 / 学生 / 人脸 ----------------

    @PostMapping("/verify/id-card")
    public ApiResponse<Map<String, Object>> verifyIdCard(@RequestBody IdCardReq req) {
        return ApiResponse.ok(identityService.verifyIdCard(UserContext.requireUserId(),
                req.realName(), req.idNo(), req.frontUrl(), req.backUrl()));
    }

    @PostMapping("/verify/student")
    public ApiResponse<Map<String, Object>> verifyStudent(@RequestBody StudentReq req) {
        return ApiResponse.ok(identityService.verifyStudent(UserContext.requireUserId(),
                req.schoolName(), req.studentNo(), req.coverUrl(), req.infoUrl()));
    }

    @PostMapping("/verify/face")
    public ApiResponse<Map<String, Object>> verifyFace(@RequestBody FaceReq req) {
        return ApiResponse.ok(identityService.verifyFace(UserContext.requireUserId(), req.imageUrl()));
    }

    @GetMapping("/verify/status")
    public ApiResponse<Map<String, Object>> verifyStatus() {
        return ApiResponse.ok(identityService.status(UserContext.requireUserId()));
    }

    // ---------------- ISBN ----------------

    @GetMapping("/isbn/{isbn}")
    public ApiResponse<Map<String, Object>> isbn(@PathVariable String isbn) {
        return ApiResponse.ok(isbnService.lookup(isbn));
    }

    /** 拍照识别书背 EAN-13 条码（本地 ZXing 解码，离线可用），识别后自动查书名。 */
    @PostMapping("/isbn/scan")
    public ApiResponse<Map<String, Object>> scanIsbn(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(isbnScanService.scan(file));
    }

    // ---------------- 商户端 ----------------

    public record MerchantApplyReq(String shopName, String licenseUrl, String contact) {
    }

    public record BatchReq(List<MerchantService.Draft> listings) {
    }

    public record OfflineReq(List<Long> listingIds) {
    }

    @PostMapping("/merchant/apply")
    public ApiResponse<?> merchantApply(@RequestBody MerchantApplyReq req) {
        return ApiResponse.ok(merchantService.apply(UserContext.requireUserId(),
                req.shopName(), req.licenseUrl(), req.contact()));
    }

    @GetMapping("/merchant/my")
    public ApiResponse<?> merchantMy() {
        return ApiResponse.ok(merchantService.my(UserContext.requireUserId()));
    }

    @GetMapping("/merchant/stats")
    public ApiResponse<Map<String, Object>> merchantStats() {
        return ApiResponse.ok(merchantService.stats(UserContext.requireUserId()));
    }

    @PostMapping("/merchant/listings/batch")
    public ApiResponse<Map<String, Object>> merchantBatch(@RequestBody BatchReq req) {
        return ApiResponse.ok(merchantService.batchPublish(UserContext.requireUserId(), req.listings()));
    }

    @PostMapping("/merchant/listings/offline")
    public ApiResponse<Map<String, Object>> merchantOffline(@RequestBody OfflineReq req) {
        return ApiResponse.ok(merchantService.batchOffline(UserContext.requireUserId(), req.listingIds()));
    }

    // ---------------- 商户工作台（P3：商品 / 订单 / 数据 / 结算） ----------------

    public record ToggleReq(boolean online) {
    }

    public record PriceReq(List<Long> listingIds, java.math.BigDecimal value, String mode) {
    }

    @GetMapping("/merchant/listings")
    public ApiResponse<?> merchantListings(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(merchantService.listings(UserContext.requireUserId(), status, keyword, page, size));
    }

    @PostMapping("/merchant/listings/{id}/toggle")
    public ApiResponse<Map<String, Object>> merchantToggle(@PathVariable Long id,
                                                          @RequestBody(required = false) ToggleReq req) {
        boolean online = req != null && req.online();
        return ApiResponse.ok(merchantService.toggleListing(UserContext.requireUserId(), id, online));
    }

    @PostMapping("/merchant/listings/price")
    public ApiResponse<Map<String, Object>> merchantPrice(@RequestBody PriceReq req) {
        return ApiResponse.ok(merchantService.batchPrice(UserContext.requireUserId(),
                req.listingIds(), req.value(), req.mode()));
    }

    @GetMapping("/merchant/orders")
    public ApiResponse<?> merchantOrders(@RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(merchantService.orders(UserContext.requireUserId(), status, page, size));
    }

    @GetMapping("/merchant/dashboard")
    public ApiResponse<Map<String, Object>> merchantDashboard() {
        return ApiResponse.ok(merchantService.dashboard(UserContext.requireUserId()));
    }

    @GetMapping("/merchant/settlements")
    public ApiResponse<?> merchantSettlements(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(merchantService.settlements(UserContext.requireUserId(), page, size));
    }

    /** 店铺主页（游客可看）：店铺资料 + 汇总指标 + 在售商品分页 + 最近评价 */
    @GetMapping("/shops/{merchantId}")
    public ApiResponse<Map<String, Object>> shop(@PathVariable Long merchantId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "12") long size) {
        return ApiResponse.ok(merchantService.shop(merchantId, page, size));
    }
}
