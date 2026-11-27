package com.textbook.loop.controller;

import com.textbook.loop.common.ApiResponse;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizTicket;
import com.textbook.loop.domain.BizTicketMessage;
import com.textbook.loop.security.UserContext;
import com.textbook.loop.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 纠纷工单（用户侧）。
 */
@RestController
@RequestMapping("/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public record CreateReq(String orderNo, String type, String title, String description) {
    }

    public record ReplyReq(String content) {
    }

    @PostMapping
    public ApiResponse<BizTicket> create(@RequestBody CreateReq req) {
        return ApiResponse.ok(ticketService.create(UserContext.requireUserId(), req.orderNo(), req.type(),
                req.title(), req.description()));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<BizTicket>> mine(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(ticketService.listMine(UserContext.requireUserId(), page, size));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<BizTicketMessage>> messages(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.messages(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<BizTicketMessage> reply(@PathVariable Long id, @RequestBody ReplyReq req) {
        return ApiResponse.ok(ticketService.reply(UserContext.requireUserId(), id, req.content()));
    }
}
