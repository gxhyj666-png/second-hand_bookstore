package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.domain.BizPickupPoint;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.PickupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 自提点：同学选点、店员看自己负责的点。
 */
@RestController
@RequestMapping("/v1/pickup-points")
public class PickupController {

    private final PickupService pickupService;

    public PickupController(PickupService pickupService) {
        this.pickupService = pickupService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long schoolId,
                                                       @RequestParam(required = false) Double lat,
                                                       @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(pickupService.list(schoolId, lat, lng));
    }

    @GetMapping("/{id}")
    public ApiResponse<BizPickupPoint> get(@PathVariable Long id) {
        return ApiResponse.ok(pickupService.get(id));
    }

    @GetMapping("/mine")
    public ApiResponse<List<BizPickupPoint>> mine() {
        return ApiResponse.ok(pickupService.pointsOfManager(UserContext.requireUserId()));
    }
}
