package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.BizGroupBuy;
import com.textbook.loop.domain.BizGroupBuyItem;
import com.textbook.loop.domain.SysCourse;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizGroupBuyItemMapper;
import com.textbook.loop.mapper.BizGroupBuyMapper;
import com.textbook.loop.mapper.SysCourseMapper;
import com.textbook.loop.mapper.SysTextbookMapper;
import com.textbook.loop.mapper.SysUserMapper;
import com.textbook.loop.util.BizNo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 班级接龙：团长按课程发起统一收书/订书，同学用邀请码登记需求。
 */
@Service
public class GroupBuyService {

    private final BizGroupBuyMapper groupMapper;
    private final BizGroupBuyItemMapper itemMapper;
    private final SysCourseMapper courseMapper;
    private final SysTextbookMapper textbookMapper;
    private final SysUserMapper userMapper;
    private final NoticeService noticeService;

    public GroupBuyService(BizGroupBuyMapper groupMapper, BizGroupBuyItemMapper itemMapper,
                           SysCourseMapper courseMapper, SysTextbookMapper textbookMapper,
                           SysUserMapper userMapper, NoticeService noticeService) {
        this.groupMapper = groupMapper;
        this.itemMapper = itemMapper;
        this.courseMapper = courseMapper;
        this.textbookMapper = textbookMapper;
        this.userMapper = userMapper;
        this.noticeService = noticeService;
    }

    @Transactional
    public BizGroupBuy create(Long leaderId, Long courseId, Long textbookId, String title, Integer targetCount,
                              BigDecimal unitPrice, LocalDateTime deadline) {
        if (targetCount == null || targetCount <= 0) {
            throw BizException.badRequest("目标册数必须大于 0");
        }
        BizGroupBuy g = new BizGroupBuy();
        g.setCourseId(courseId);
        g.setTextbookId(textbookId);
        g.setLeaderId(leaderId);
        g.setTitle(title);
        g.setTargetCount(targetCount);
        g.setUnitPrice(unitPrice);
        g.setInviteCode(BizNo.inviteCode());
        g.setCurrentCount(0);
        g.setDeadline(deadline == null ? LocalDateTime.now().plusDays(7) : deadline);
        g.setStatus("OPEN");
        g.setCommissionRate(new BigDecimal("0.02"));
        groupMapper.insert(g);
        return g;
    }

    public PageResult<BizGroupBuy> list(String status, long page, long size) {
        return PageResult.of(groupMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<BizGroupBuy>()
                .eq(status != null && !status.isBlank(), BizGroupBuy::getStatus, status)
                .orderByDesc(BizGroupBuy::getId)));
    }

    public Map<String, Object> detail(String inviteCode) {
        BizGroupBuy g = groupMapper.selectOne(new LambdaQueryWrapper<BizGroupBuy>()
                .eq(BizGroupBuy::getInviteCode, inviteCode).last("limit 1"));
        if (g == null) {
            throw BizException.notFound("接龙不存在或邀请码已失效");
        }
        SysCourse course = g.getCourseId() == null ? null : courseMapper.selectById(g.getCourseId());
        SysTextbook book = g.getTextbookId() == null ? null : textbookMapper.selectById(g.getTextbookId());
        SysUser leader = userMapper.selectById(g.getLeaderId());
        List<BizGroupBuyItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizGroupBuyItem>()
                .eq(BizGroupBuyItem::getGroupBuyId, g.getId())
                .orderByAsc(BizGroupBuyItem::getId));

        List<Map<String, Object>> participants = items.stream().map(i -> {
            SysUser u = userMapper.selectById(i.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", i.getUserId());
            m.put("nickname", u == null ? "同学" : u.getNickname());
            m.put("quantity", i.getQuantity());
            m.put("status", i.getStatus());
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("group", g);
        result.put("courseName", course == null ? "" : course.getName());
        result.put("textbookTitle", book == null ? "" : book.getTitle() + " " + (book.getEdition() == null ? "" : book.getEdition()));
        result.put("leaderName", leader == null ? "" : leader.getNickname());
        result.put("participants", participants);
        result.put("progress", g.getTargetCount() == null || g.getTargetCount() == 0
                ? 0 : Math.min(100, (g.getCurrentCount() == null ? 0 : g.getCurrentCount()) * 100 / g.getTargetCount()));
        return result;
    }

    @Transactional
    public BizGroupBuy join(Long userId, String inviteCode, Integer quantity, String contact) {
        BizGroupBuy g = groupMapper.selectOne(new LambdaQueryWrapper<BizGroupBuy>()
                .eq(BizGroupBuy::getInviteCode, inviteCode).last("limit 1"));
        if (g == null) {
            throw BizException.notFound("接龙不存在或邀请码已失效");
        }
        if (!"OPEN".equals(g.getStatus())) {
            throw BizException.conflict("接龙已关闭");
        }
        if (g.getDeadline() != null && LocalDateTime.now().isAfter(g.getDeadline())) {
            throw BizException.conflict("接龙已截止");
        }
        int qty = quantity == null || quantity <= 0 ? 1 : quantity;

        BizGroupBuyItem item = itemMapper.selectOne(new LambdaQueryWrapper<BizGroupBuyItem>()
                .eq(BizGroupBuyItem::getGroupBuyId, g.getId())
                .eq(BizGroupBuyItem::getUserId, userId)
                .last("limit 1"));
        if (item == null) {
            item = new BizGroupBuyItem();
            item.setGroupBuyId(g.getId());
            item.setUserId(userId);
            item.setQuantity(qty);
            item.setContact(contact);
            item.setStatus("JOINED");
            item.setCreatedAt(LocalDateTime.now());
            itemMapper.insert(item);
        } else {
            item.setQuantity(qty);
            item.setContact(contact);
            item.setStatus("JOINED");
            itemMapper.updateById(item);
        }

        List<BizGroupBuyItem> all = itemMapper.selectList(new LambdaQueryWrapper<BizGroupBuyItem>()
                .eq(BizGroupBuyItem::getGroupBuyId, g.getId())
                .eq(BizGroupBuyItem::getStatus, "JOINED"));
        int total = all.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        g.setCurrentCount(total);
        if (g.getTargetCount() != null && total >= g.getTargetCount()) {
            g.setStatus("DONE");
            noticeService.push(g.getLeaderId(), "SYSTEM", "接龙已达成目标",
                    "「" + g.getTitle() + "」已登记 " + total + " 册，可以统一采购/收书了。", "GROUP_BUY", g.getId());
        }
        groupMapper.updateById(g);
        return g;
    }

    @Transactional
    public BizGroupBuy close(Long leaderId, String inviteCode) {
        BizGroupBuy g = groupMapper.selectOne(new LambdaQueryWrapper<BizGroupBuy>()
                .eq(BizGroupBuy::getInviteCode, inviteCode).last("limit 1"));
        if (g == null) {
            throw BizException.notFound("接龙不存在");
        }
        if (!g.getLeaderId().equals(leaderId)) {
            throw BizException.forbidden("只有团长可以关闭接龙");
        }
        g.setStatus("CLOSED");
        groupMapper.updateById(g);
        return g;
    }
}
