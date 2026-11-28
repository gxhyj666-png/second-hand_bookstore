package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.AuthService;
import com.textbook.loop.service.MeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人中心：我的数据 / 我的发布 / 我的评价 / 他人主页 / 资料字典 / 换绑手机号。
 */
@RestController
@RequestMapping("/v1")
public class MeController {

    private final MeService meService;
    private final AuthService authService;

    public MeController(MeService meService, AuthService authService) {
        this.meService = meService;
        this.authService = authService;
    }

    public record PhoneReq(String phone, String code) {
    }

    @GetMapping("/my/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(meService.stats(UserContext.requireUserId()));
    }

    @GetMapping("/my/reviews")
    public ApiResponse<PageResult<Map<String, Object>>> myReviews(
            @RequestParam(defaultValue = "received") String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(meService.myReviews(UserContext.requireUserId(), type, page, size));
    }

    /** 他人主页（游客可看）。 */
    @GetMapping("/users/{id}/profile")
    public ApiResponse<Map<String, Object>> profile(@PathVariable Long id) {
        return ApiResponse.ok(meService.profile(id));
    }

    /** 资料页字典：学院 / 专业 / 年级。 */
    @GetMapping("/meta/profile-options")
    public ApiResponse<Map<String, Object>> profileOptions() {
        return ApiResponse.ok(meService.profileOptions());
    }

    /** 换绑手机号：先对该号码调 /v1/auth/sms-code（purpose=BIND_PHONE）拿码。 */
    @PutMapping("/me/phone")
    public ApiResponse<Object> changePhone(@RequestBody PhoneReq req) {
        return ApiResponse.ok(authService.changePhone(UserContext.requireUserId(), req.phone(), req.code()));
    }
}
