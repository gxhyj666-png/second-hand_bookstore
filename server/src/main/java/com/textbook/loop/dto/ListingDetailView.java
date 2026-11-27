package com.textbook.loop.dto;

import com.textbook.loop.domain.BizListing;
import com.textbook.loop.domain.BizListingImage;
import com.textbook.loop.domain.SysCourse;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.domain.SysUser;

import java.util.List;

/**
 * 商品详情聚合视图。
 */
public record ListingDetailView(BizListing listing, SysTextbook textbook, SysCourse course,
                                SellerView seller, List<BizListingImage> images, boolean favored) {

    public record SellerView(Long id, String nickname, String avatar, String college, String dorm,
                             Integer creditScore, String verifyState) {
        public static SellerView of(SysUser u) {
            if (u == null) {
                return null;
            }
            return new SellerView(u.getId(), u.getNickname(), u.getAvatar(), u.getCollege(), u.getDorm(),
                    u.getCreditScore(), u.getVerifyState());
        }
    }
}
