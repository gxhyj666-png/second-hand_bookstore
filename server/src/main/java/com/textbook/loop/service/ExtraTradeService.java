package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.textbook.loop.common.BizException;
import com.textbook.loop.common.PageResult;
import com.textbook.loop.domain.*;
import com.textbook.loop.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 商品照片（打磨清单第 4 条：封面 / 封底 / 出版日期页 / 随机四页正文）与买卖私信。
 */
@Service
public class ExtraTradeService {

    private final BizListingMapper listingMapper;
    private final BizListingPhotoMapper photoMapper;
    private final BizConversationMapper conversationMapper;
    private final BizMessageMapper messageMapper;
    private final SysUserMapper userMapper;

    public ExtraTradeService(BizListingMapper listingMapper, BizListingPhotoMapper photoMapper,
                             BizConversationMapper conversationMapper, BizMessageMapper messageMapper,
                             SysUserMapper userMapper) {
        this.listingMapper = listingMapper;
        this.photoMapper = photoMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
    }

    // ------------------------------------------------------------------ 照片

    /** 必传类型：COVER / BACK / PUBLISH_DATE / CONTENT1..4 至少各一张，缺哪类就提示哪类。 */
    private static final List<String> REQUIRED = List.of("COVER", "BACK", "PUBLISH_DATE");
    private static final Set<String> ALLOWED = Set.of("COVER", "BACK", "PUBLISH_DATE",
            "CONTENT1", "CONTENT2", "CONTENT3", "CONTENT4", "EXTRA");

    public record PhotoItem(String type, String url) {
    }

    @Transactional
    public Map<String, Object> savePhotos(Long ownerId, Long listingId, List<PhotoItem> photos) {
        BizListing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw BizException.notFound("商品不存在");
        }
        if (!listing.getSellerId().equals(ownerId)) {
            throw BizException.forbidden("只能操作自己的商品");
        }
        if (photos == null || photos.isEmpty()) {
            throw BizException.badRequest("请至少上传一张照片");
        }
        photoMapper.delete(new LambdaQueryWrapper<BizListingPhoto>().eq(BizListingPhoto::getListingId, listingId));

        int sort = 0;
        String cover = "";
        for (PhotoItem item : photos) {
            String type = item.type() == null ? "EXTRA" : item.type().toUpperCase();
            if (!ALLOWED.contains(type)) {
                throw BizException.badRequest("不支持的照片类型：" + type);
            }
            if (item.url() == null || item.url().isBlank()) {
                throw BizException.badRequest("照片地址不能为空");
            }
            BizListingPhoto photo = new BizListingPhoto();
            photo.setListingId(listingId);
            photo.setPhotoType(type);
            photo.setUrl(item.url().trim());
            photo.setSortNo(sort++);
            photoMapper.insert(photo);
            if ("COVER".equals(type) && cover.isEmpty()) {
                cover = item.url().trim();
            }
        }
        if (cover.isEmpty() && !photos.isEmpty()) {
            cover = photos.get(0).url();
        }
        listing.setCoverUrl(cover);
        listing.setImageCount(photos.size());
        listingMapper.updateById(listing);

        List<String> missing = REQUIRED.stream()
                .filter(t -> photos.stream().noneMatch(p -> t.equalsIgnoreCase(p.type())))
                .toList();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("count", photos.size());
        map.put("missing", missing);
        map.put("complete", missing.isEmpty());
        return map;
    }

    public List<BizListingPhoto> photosOf(Long listingId) {
        return photoMapper.selectList(new LambdaQueryWrapper<BizListingPhoto>()
                .eq(BizListingPhoto::getListingId, listingId)
                .orderByAsc(BizListingPhoto::getSortNo));
    }

    // ------------------------------------------------------------------ 私信

    /**
     * 打开（或创建）针对某商品的会话；同一买家同一商品只有一个会话。
     */
    @Transactional
    public BizConversation openConversation(Long buyerId, Long listingId) {
        BizListing listing = listingMapper.selectById(listingId);
        if (listing == null) {
            throw BizException.notFound("商品不存在");
        }
        if (listing.getSellerId().equals(buyerId)) {
            throw BizException.badRequest("不能与自己聊天，可先去商品详情页预览");
        }
        BizConversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<BizConversation>()
                .eq(BizConversation::getListingId, listingId)
                .eq(BizConversation::getBuyerId, buyerId)
                .eq(BizConversation::getSellerId, listing.getSellerId())
                .last("limit 1"));
        if (conv == null) {
            conv = new BizConversation();
            conv.setListingId(listingId);
            conv.setBuyerId(buyerId);
            conv.setSellerId(listing.getSellerId());
            conv.setLastMessage("");
            conv.setLastAt(LocalDateTime.now());
            conv.setBuyerUnread(0);
            conv.setSellerUnread(0);
            conversationMapper.insert(conv);
        }
        return conv;
    }

    public PageResult<Map<String, Object>> conversations(Long userId, long page, long size) {
        Page<BizConversation> p = conversationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BizConversation>()
                        .and(w -> w.eq(BizConversation::getBuyerId, userId).or().eq(BizConversation::getSellerId, userId))
                        .orderByDesc(BizConversation::getLastAt));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizConversation conv : p.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("conversation", conv);
            BizListing listing = listingMapper.selectById(conv.getListingId());
            row.put("listingTitle", listing == null ? "" : listing.getTitle());
            row.put("listingCover", listing == null ? "" : listing.getCoverUrl());
            Long other = conv.getBuyerId().equals(userId) ? conv.getSellerId() : conv.getBuyerId();
            SysUser otherUser = userMapper.selectById(other);
            row.put("otherName", otherUser == null ? "" : otherUser.getNickname());
            row.put("unread", conv.getBuyerId().equals(userId) ? conv.getBuyerUnread() : conv.getSellerUnread());
            rows.add(row);
        }
        return new PageResult<>(rows, p.getTotal(), page, size);
    }

    private Long extractRef(String extra) {
        if (extra == null || extra.isBlank()) {
            return 0L;
        }
        try {
            return Long.valueOf(extra.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private BizConversation requireParticipant(Long userId, Long conversationId) {
        BizConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw BizException.notFound("会话不存在");
        }
        if (!conv.getBuyerId().equals(userId) && !conv.getSellerId().equals(userId)) {
            throw BizException.forbidden("无权访问该会话");
        }
        return conv;
    }

    public List<BizMessage> messages(Long userId, Long conversationId, Long afterId) {
        BizConversation conv = requireParticipant(userId, conversationId);
        List<BizMessage> list = messageMapper.selectList(new LambdaQueryWrapper<BizMessage>()
                .eq(BizMessage::getConversationId, conversationId)
                .gt(afterId != null && afterId > 0, BizMessage::getId, afterId)
                .orderByAsc(BizMessage::getId));
        // 打开会话即清自己的未读
        if (conv.getBuyerId().equals(userId)) {
            conv.setBuyerUnread(0);
        } else {
            conv.setSellerUnread(0);
        }
        conversationMapper.updateById(conv);
        return list;
    }

    @Transactional
    public BizMessage send(Long userId, Long conversationId, String type, String content, String extra) {
        BizConversation conv = requireParticipant(userId, conversationId);
        if (content == null || content.isBlank()) {
            throw BizException.badRequest("消息内容不能为空");
        }
        String t = (type == null || type.isBlank()) ? "TEXT" : type.toUpperCase();
        BizMessage msg = new BizMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(userId);
        msg.setMsgType(t);
        msg.setContent(content.trim());
        msg.setRefId(extractRef(extra));
        msg.setReadFlag(0);
        messageMapper.insert(msg);

        conv.setLastMessage(content.trim());
        conv.setLastAt(LocalDateTime.now());
        if (conv.getBuyerId().equals(userId)) {
            conv.setSellerUnread((conv.getSellerUnread() == null ? 0 : conv.getSellerUnread()) + 1);
        } else {
            conv.setBuyerUnread((conv.getBuyerUnread() == null ? 0 : conv.getBuyerUnread()) + 1);
        }
        conversationMapper.updateById(conv);
        return msg;
    }
}

