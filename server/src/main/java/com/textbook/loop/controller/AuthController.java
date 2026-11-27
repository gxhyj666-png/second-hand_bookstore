package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.dto.TokenView;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与个人资料。
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterReq(String phone, String password, String nickname) {
    }

    public record LoginReq(String account, String password) {
    }

    public record WechatReq(String code) {
    }

    public record ProfileReq(String nickname, String avatar, String college, String major, String grade, String dorm) {
    }

    public record VerifyReq(String studentNo, String realName) {
    }

    @PostMapping("/register")
    public ApiResponse<TokenView> register(@RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req.phone(), req.password(), req.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenView> login(@RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req.account(), req.password()));
    }

    @PostMapping("/wechat")
    public ApiResponse<TokenView> wechat(@RequestBody WechatReq req) {
        return ApiResponse.ok(authService.wechat(req.code()));
    }

    @GetMapping("/me")
    public ApiResponse<Object> me() {
        return ApiResponse.ok(authService.me(UserContext.requireUserId()));
    }

    @PutMapping("/profile")
    public ApiResponse<Object> updateProfile(@RequestBody ProfileReq req) {
        return ApiResponse.ok(authService.updateProfile(UserContext.requireUserId(), req.nickname(), req.avatar(),
                req.college(), req.major(), req.grade(), req.dorm()));
    }

    @PostMapping("/verify-student")
    public ApiResponse<Object> verifyStudent(@RequestBody VerifyReq req) {
        return ApiResponse.ok(authService.verifyStudent(UserContext.requireUserId(), req.studentNo(), req.realName()));
    }
}
