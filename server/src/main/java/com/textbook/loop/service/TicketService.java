package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizTicket;
import com.textbook.loop.domain.BizTicketMessage;
import com.textbook.loop.mapper.BizTicketMapper;
import com.textbook.loop.mapper.BizTicketMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 纠纷工单：用户发起 → 双方留言 → 管理员处理。
 */
@Service
public class TicketService {

    private final BizTicketMapper ticketMapper;
    private final BizTicketMessageMapper messageMapper;
    private final NoticeService noticeService;

    public TicketService(BizTicketMapper ticketMapper, BizTicketMessageMapper messageMapper,
                         NoticeService noticeService) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
        this.noticeService = noticeService;
    }

    @Transactional
    public BizTicket create(Long userId, String orderNo, String type, String title, String description) {
        BizTicket t = new BizTicket();
        t.setOrderNo(orderNo);
        t.setInitiatorId(userId);
        t.setType(type == null ? "OTHER" : type);
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus("PENDING");
        t.setCreatedAt(LocalDateTime.now());
        ticketMapper.insert(t);
        noticeService.push(userId, "TICKET", "工单已提交", "工单 #" + t.getId() + " 已受理，我们会尽快处理。",
                "TICKET", t.getId());
        return t;
    }

    public PageResult<BizTicket> listMine(Long userId, long page, long size) {
        return PageResult.of(ticketMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizTicket>()
                .eq(BizTicket::getInitiatorId, userId)
                .orderByDesc(BizTicket::getId)));
    }

    public PageResult<BizTicket> listAll(String status, long page, long size) {
        return PageResult.of(ticketMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizTicket>()
                .eq(status != null && !status.isBlank(), BizTicket::getStatus, status)
                .orderByDesc(BizTicket::getId)));
    }

    @Transactional
    public BizTicketMessage reply(Long userId, Long ticketId, String content) {
        BizTicket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw BizException.notFound("工单不存在");
        }
        boolean admin = "ADMIN".equals(com.textbook.loop.security.UserContext.require().role());
        if (!admin && !t.getInitiatorId().equals(userId)) {
            throw BizException.forbidden("无权回复该工单");
        }
        if (!admin && "PENDING".equals(t.getStatus())) {
            t.setStatus("PROCESSING");
            ticketMapper.updateById(t);
        }
        BizTicketMessage m = new BizTicketMessage();
        m.setTicketId(ticketId);
        m.setSenderId(userId);
        m.setSenderRole(admin ? "ADMIN" : "USER");
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        noticeService.push(t.getInitiatorId(), "TICKET", "工单有新回复", content, "TICKET", ticketId);
        return m;
    }

    public List<BizTicketMessage> messages(Long ticketId) {
        return messageMapper.selectList(new LambdaQueryWrapper<BizTicketMessage>()
                .eq(BizTicketMessage::getTicketId, ticketId)
                .orderByAsc(BizTicketMessage::getId));
    }

    @Transactional
    public BizTicket resolve(Long handlerId, Long ticketId, String status, String result) {
        BizTicket t = ticketMapper.selectById(ticketId);
        if (t == null) {
            throw BizException.notFound("工单不存在");
        }
        t.setStatus(status == null ? "RESOLVED" : status);
        t.setResult(result);
        t.setHandlerId(handlerId);
        t.setClosedAt(LocalDateTime.now());
        ticketMapper.updateById(t);
        noticeService.push(t.getInitiatorId(), "TICKET", "工单已处理",
                "工单 #" + ticketId + " 处理结果：" + (result == null ? "已处理" : result), "TICKET", ticketId);
        return t;
    }
}
