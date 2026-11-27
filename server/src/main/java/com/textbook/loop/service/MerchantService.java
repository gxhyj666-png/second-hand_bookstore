package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.common.BizException;
import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.SysMerchant;
import com.textbook.loop.domain.SysUser;
import com.textbook.loop.mapper.BizListingMapper;
import com.textbook.loop.mapper.SysMerchantMapper;
import com.textbook.loop.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商户端：入驻申请、批量上下架、商户看板。
 * 书籍量大的商家走这条链路，个人卖家仍用 /v1/listings 单件发布。
 */
@Service
public class MerchantService {

    private final SysMerchantMapper merchantMapper;
    private final SysUserMapper userMapper;
    private final BizListingMapper listingMapper;
    private final NoticeService noticeService;

    public MerchantService(SysMerchantMapper merchantMapper, SysUserMapper userMapper,
                           BizListingMapper listingMapper, NoticeService noticeService) {
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.listingMapper = listingMapper;
        this.noticeService = noticeService;
    }

    /** 入驻申请：提交后进入 PENDING，平台审核。 */
    @Transactional
    public SysMerchant apply(Long userId, String shopName, String licenseUrl, String contact) {
        if (shopName == null || shopName.isBlank()) {
            throw BizException.badRequest("请填写店铺名称");
        }
        SysMerchant exists = merchantMapper.selectOne(new LambdaQueryWrapper<SysMerchant>()
                .eq(SysMerchant::getUserId, userId).last("limit 1"));
        if (exists != null && "APPROVED".equals(exists.getStatus())) {
            throw BizException.conflict("你已入驻商户，无需重复申请");
        }
        SysMerchant m = exists == null ? new SysMerchant() : exists;
        m.setUserId(userId);
        m.setShopName(shopName.trim());
        m.setLicenseUrl(licenseUrl == null ? "" : licenseUrl);
        m.setContact(contact == null ? "" : contact);
        m.setStatus("PENDING");
        m.setRejectReason("");
        if (exists == null) {
            merchantMapper.insert(m);
        } else {
            merchantMapper.updateById(m);
        }
        return m;
    }

    public SysMerchant my(Long userId) {
        return merchantMapper.selectOne(new LambdaQueryWrapper<SysMerchant>()
                .eq(SysMerchant::getUserId, userId).last("limit 1"));
    }

    public List<SysMerchant> adminList(String status) {
        LambdaQueryWrapper<SysMerchant> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(SysMerchant::getStatus, status);
        }
        return merchantMapper.selectList(q.orderByDesc(SysMerchant::getId));
    }

    @Transactional
    public SysMerchant review(Long adminId, Long merchantId, boolean approved, String reason) {
        SysMerchant m = merchantMapper.selectById(merchantId);
        if (m == null) {
            throw BizException.notFound("商户不存在");
        }
        if (!"PENDING".equals(m.getStatus())) {
            throw BizException.conflict("该商户当前状态不可审核：" + m.getStatus());
        }
        m.setStatus(approved ? "APPROVED" : "REJECTED");
        m.setRejectReason(approved ? "" : (reason == null ? "资料不完整" : reason));
        m.setApprovedBy(adminId);
        m.setApprovedAt(LocalDateTime.now());
        merchantMapper.updateById(m);
        SysUser user = userMapper.selectById(m.getUserId());
        if (user != null && approved) {
            user.setRole("MERCHANT");
            user.setMerchantId(m.getId());
            userMapper.updateById(user);
            noticeService.push(user.getId(), "SYSTEM", "商户入驻已通过",
                    "店铺「" + m.getShopName() + "」已开通，可批量上下架图书", "MERCHANT", m.getId());
        } else if (user != null) {
            noticeService.push(user.getId(), "SYSTEM", "商户入驻未通过",
                    m.getRejectReason(), "MERCHANT", m.getId());
        }
        return m;
    }

    /** 批量发布：单次最多 100 条，任一校验失败整批回滚。 */
    @Transactional
    public Map<String, Object> batchPublish(Long userId, List<Draft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw BizException.badRequest("请至少提交一本书");
        }
        if (drafts.size() > 100) {
            throw BizException.badRequest("单次最多批量上传 100 本");
        }
        SysMerchant merchant = requireApproved(userId);
        List<Long> ids = new ArrayList<>();
        for (Draft d : drafts) {
            if (d.title() == null || d.title().isBlank()) {
                throw BizException.badRequest("书名不能为空");
            }
            if (d.price() == null || d.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw BizException.badRequest("价格必须大于 0：" + d.title());
            }
            BizListing l = new BizListing();
            l.setSellerId(userId);
            l.setMerchantId(merchant.getId());
            l.setTextbookId(d.textbookId() == null ? 0L : d.textbookId());
            l.setCourseId(d.courseId() == null ? 0L : d.courseId());
            l.setTitle(d.title().trim());
            l.setPrice(d.price());
            l.setOriginalPrice(d.originalPrice() == null ? BigDecimal.ZERO : d.originalPrice());
            l.setConditionLevel(d.conditionLevel() == null ? "GOOD" : d.conditionLevel());
            l.setDescription(d.description() == null ? "" : d.description());
            l.setCoverUrl(d.coverUrl() == null ? "" : d.coverUrl());
            l.setImageCount(d.imageCount() == null ? 0 : d.imageCount());
            l.setDorm(d.dorm() == null ? "商户仓库" : d.dorm());
            l.setStatus("ON_SALE");
            l.setViewCount(0);
            l.setFavoriteCount(0);
            l.setPublishedAt(LocalDateTime.now());
            listingMapper.insert(l);
            ids.add(l.getId());
        }
        merchant.setListingCount((int) countOnSale(merchant.getId()));
        merchantMapper.updateById(merchant);
        Map<String, Object> map = new HashMap<>();
        map.put("count", ids.size());
        map.put("listingIds", ids);
        return map;
    }

    @Transactional
    public Map<String, Object> batchOffline(Long userId, List<Long> listingIds) {
        requireApproved(userId);
        if (listingIds == null || listingIds.isEmpty()) {
            throw BizException.badRequest("请选择要下架的商品");
        }
        int changed = 0;
        for (Long id : listingIds) {
            BizListing l = listingMapper.selectById(id);
            if (l == null || !userId.equals(l.getSellerId())) {
                continue;
            }
            if (!"ON_SALE".equals(l.getStatus())) {
                continue;
            }
            l.setStatus("OFFLINE");
            listingMapper.updateById(l);
            changed++;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("count", changed);
        return map;
    }

    public Map<String, Object> stats(Long userId) {
        SysMerchant m = requireApproved(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("merchantId", m.getId());
        map.put("shopName", m.getShopName());
        map.put("onSale", countOnSale(m.getId()));
        map.put("total", listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, m.getId())));
        return map;
    }

    private long countOnSale(Long merchantId) {
        Long n = listingMapper.selectCount(new LambdaQueryWrapper<BizListing>()
                .eq(BizListing::getMerchantId, merchantId)
                .eq(BizListing::getStatus, "ON_SALE"));
        return n == null ? 0 : n;
    }

    private SysMerchant requireApproved(Long userId) {
        SysMerchant m = my(userId);
        if (m == null) {
            throw BizException.forbidden("请先申请入驻商户");
        }
        if (!"APPROVED".equals(m.getStatus())) {
            throw BizException.forbidden("商户尚未通过审核：" + m.getStatus());
        }
        return m;
    }

    /** 批量上传的单条图书信息。 */
    public record Draft(String title, Long textbookId, Long courseId, BigDecimal price, BigDecimal originalPrice,
                        String conditionLevel, String description, String coverUrl, Integer imageCount,
                        String dorm) {
    }
}
