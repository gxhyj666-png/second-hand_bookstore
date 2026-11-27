package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizConversation;
import com.textbook.loop.domain.BizListingPhoto;
import com.textbook.loop.domain.BizMessage;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.ExtraTradeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品拍照上传（7 图规范）与买卖私信。
 */
@RestController
@RequestMapping("/v1")
public class ExtraTradeController {

    private final ExtraTradeService extraTradeService;

    public ExtraTradeController(ExtraTradeService extraTradeService) {
        this.extraTradeService = extraTradeService;
    }

    public record PhotosReq(List<ExtraTradeService.PhotoItem> photos) {
    }

    public record OpenConvReq(Long listingId) {
    }

    public record SendReq(String type, String content, String extra) {
    }

    // ---------------- 商品照片 ----------------

    @PostMapping("/listings/{id}/photos")
    public ApiResponse<Map<String, Object>> savePhotos(@PathVariable Long id, @RequestBody PhotosReq req) {
        return ApiResponse.ok(extraTradeService.savePhotos(UserContext.requireUserId(), id, req.photos()));
    }

    @GetMapping("/listings/{id}/photos")
    public ApiResponse<List<BizListingPhoto>> photos(@PathVariable Long id) {
        return ApiResponse.ok(extraTradeService.photosOf(id));
    }

    // ---------------- 私信 ----------------

    @PostMapping("/conversations")
    public ApiResponse<BizConversation> open(@RequestBody OpenConvReq req) {
        return ApiResponse.ok(extraTradeService.openConversation(UserContext.requireUserId(), req.listingId()));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(extraTradeService.conversations(UserContext.requireUserId(), page, size));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<BizMessage>> messages(@PathVariable Long id,
                                                  @RequestParam(required = false) Long afterId) {
        return ApiResponse.ok(extraTradeService.messages(UserContext.requireUserId(), id, afterId));
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<BizMessage> send(@PathVariable Long id, @RequestBody SendReq req) {
        return ApiResponse.ok(extraTradeService.send(UserContext.requireUserId(), id, req.type(), req.content(), req.extra()));
    }
}
