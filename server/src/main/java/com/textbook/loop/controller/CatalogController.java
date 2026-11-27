package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.SysCourse;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.dto.CourseTextbookView;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 课程—教材知识库与推荐（公开接口，带 token 时推荐个性化）。
 */
@RestController
@RequestMapping("/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/courses")
    public ApiResponse<PageResult<SysCourse>> courses(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(catalogService.courses(keyword, page, size));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<SysCourse> course(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.course(id));
    }

    @GetMapping("/courses/{id}/textbooks")
    public ApiResponse<List<CourseTextbookView>> courseTextbooks(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.courseTextbooks(id));
    }

    @GetMapping("/textbooks")
    public ApiResponse<PageResult<SysTextbook>> textbooks(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String isbn,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(catalogService.textbooks(keyword, isbn, page, size));
    }

    @GetMapping("/textbooks/{id}")
    public ApiResponse<SysTextbook> textbook(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.textbook(id));
    }

    @GetMapping("/textbooks/{id}/price-index")
    public ApiResponse<Map<String, Object>> priceIndex(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.priceIndex(id));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<BizListing>> recommend(@RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(catalogService.recommend(UserContext.currentUserIdOrNull(), limit));
    }
}
