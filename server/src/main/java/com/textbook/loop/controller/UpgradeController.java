package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.UpgradeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 进阶：个体用户凭平台下发的进阶码升级为商户或运营。
 */
@RestController
@RequestMapping("/v1/upgrade")
public class UpgradeController {

    private final UpgradeService upgradeService;

    public UpgradeController(UpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    public record RedeemReq(String code, String shopName, String contact) {
    }

    @PostMapping("/redeem")
    public ApiResponse<Map<String, Object>> redeem(@RequestBody RedeemReq req) {
        return ApiResponse.ok(upgradeService.redeem(UserContext.requireUserId(),
                req.code(), req.shopName(), req.contact()));
    }
}
