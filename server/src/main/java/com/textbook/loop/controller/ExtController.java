package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.security.UserContext;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final MerchantService merchantService;

    public ExtController(SmsService smsService, AuthService authService, IdentityService identityService,
                         IsbnService isbnService, MerchantService merchantService) {
        this.smsService = smsService;
        this.authService = authService;
        this.identityService = identityService;
        this.isbnService = isbnService;
        this.merchantService = merchantService;
    }

    public record SmsCodeReq(String phone) {
    }

    public record SmsLoginReq(String phone, String code) {
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
        String code = smsService.send(req.phone(), "LOGIN");
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("sent", true);
        // 开发模式回传验证码，方便演示与自动化测试；生产环境 mock 关闭后为 null
        map.put("devCode", code);
        return ApiResponse.ok(map);
    }

    @PostMapping("/auth/sms-login")
    public ApiResponse<TokenView> smsLogin(@RequestBody SmsLoginReq req) {
        return ApiResponse.ok(authService.smsLogin(req.phone(), req.code()));
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
}
