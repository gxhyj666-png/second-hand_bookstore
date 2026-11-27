package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.MsgNotice;
import com.textbook.loop.mapper.MsgNoticeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息：下单、支付、核销、信用变动等都会落一条 msg_notice。
 */
@Service
public class NoticeService {

    private final MsgNoticeMapper noticeMapper;

    public NoticeService(MsgNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    public void push(Long userId, String type, String title, String content, String refType, Long refId) {
        if (userId == null) {
            return;
        }
        MsgNotice n = new MsgNotice();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setReadFlag(0);
        n.setCreatedAt(LocalDateTime.now());
        noticeMapper.insert(n);
    }

    public List<MsgNotice> list(Long userId, Integer readFlag, int limit) {
        LambdaQueryWrapper<MsgNotice> q = new LambdaQueryWrapper<MsgNotice>()
                .eq(MsgNotice::getUserId, userId)
                .orderByDesc(MsgNotice::getId)
                .last("limit " + Math.min(Math.max(limit, 1), 100));
        if (readFlag != null) {
            q.eq(MsgNotice::getReadFlag, readFlag);
        }
        return noticeMapper.selectList(q);
    }

    public PageResult<MsgNotice> page(Long userId, Integer readFlag, long page, long size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<MsgNotice> p =
                com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(page, size);
        return PageResult.of(noticeMapper.selectPage(p, new LambdaQueryWrapper<MsgNotice>()
                .eq(MsgNotice::getUserId, userId)
                .eq(readFlag != null, MsgNotice::getReadFlag, readFlag)
                .orderByDesc(MsgNotice::getId)));
    }

    public long unreadCount(Long userId) {
        return noticeMapper.selectCount(new LambdaQueryWrapper<MsgNotice>()
                .eq(MsgNotice::getUserId, userId)
                .eq(MsgNotice::getReadFlag, 0));
    }

    public void markRead(Long userId, Long noticeId) {
        MsgNotice n = noticeMapper.selectById(noticeId);
        if (n == null || !n.getUserId().equals(userId)) {
            return;
        }
        n.setReadFlag(1);
        noticeMapper.updateById(n);
    }

    public void markAllRead(Long userId) {
        MsgNotice patch = new MsgNotice();
        patch.setReadFlag(1);
        noticeMapper.update(patch, new LambdaQueryWrapper<MsgNotice>()
                .eq(MsgNotice::getUserId, userId)
                .eq(MsgNotice::getReadFlag, 0));
    }
}
