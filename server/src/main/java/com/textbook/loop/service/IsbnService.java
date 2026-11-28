package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.isbn.IsbnMetaProvider;
import com.textbook.loop.mapper.SysTextbookMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ISBN 获取书籍信息（发布页扫码/手输后自动带出书名等）。
 *
 * <p>两段式：先查本地教材库（ISBN-13 精确匹配，命中即返回，最快且离线可用）；
 * 未命中再走 {@link IsbnMetaProvider}（mock 或阿里云云市场「ISBN 标准书号查询」）。
 * 都拿不到时返回 {@code found=false}，前端引导手填。</p>
 */
@Service
public class IsbnService {

    private static final Logger log = LoggerFactory.getLogger(IsbnService.class);

    private final SysTextbookMapper textbookMapper;
    private final IsbnMetaProvider metaProvider;

    public IsbnService(SysTextbookMapper textbookMapper, IsbnMetaProvider metaProvider) {
        this.textbookMapper = textbookMapper;
        this.metaProvider = metaProvider;
    }

    public Map<String, Object> lookup(String isbn) {
        String clean = isbn == null ? "" : isbn.replaceAll("[^0-9Xx]", "");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isbn", clean);
        if (clean.length() != 10 && clean.length() != 13) {
            map.put("found", false);
            map.put("source", "invalid");
            map.put("message", "ISBN 应为 10 位或 13 位");
            return map;
        }
        if (clean.length() == 13 && !isValidIsbn13(clean)) {
            // 校验位不对就不必再花一次第三方额度
            map.put("found", false);
            map.put("source", "invalid");
            map.put("message", "ISBN 校验位不正确，请核对书背条形码下方的 13 位数字");
            return map;
        }

        // 1) 本地教材库优先
        SysTextbook book = textbookMapper.selectOne(new LambdaQueryWrapper<SysTextbook>()
                .eq(SysTextbook::getIsbn13, clean).last("limit 1"));
        if (book != null) {
            map.put("found", true);
            map.put("source", "local");
            map.put("textbookId", book.getId());
            map.put("title", book.getTitle());
            map.put("author", book.getAuthor());
            map.put("publisher", book.getPublisher());
            map.put("edition", book.getEdition());
            map.put("price", book.getPrice());
            map.put("category", book.getCategory());
            return map;
        }

        // 2) 第三方图书库兜底
        IsbnMetaProvider.BookMeta meta = metaProvider.lookup(clean);
        if (!meta.found()) {
            map.put("found", false);
            map.put("source", meta.source());
            map.put("message", meta.message());
            return map;
        }
        log.info("ISBN {} 由 {} 命中：{}", clean, metaProvider.name(), meta.title());
        map.put("found", true);
        map.put("source", meta.source());
        map.put("title", meta.title());
        map.put("author", meta.author());
        map.put("publisher", meta.publisher());
        map.put("pubDate", meta.pubDate());
        map.put("edition", meta.edition());
        map.put("price", meta.price());
        map.put("coverUrl", meta.coverUrl());
        map.put("gist", meta.gist());
        map.put("message", "本地教材库未收录，数据来自第三方图书库，发布时会以你填写的为准");
        return map;
    }

    /** EAN-13 校验位：奇数位 ×1、偶数位 ×3 求和，校验位 = (10 - sum % 10) % 10。 */
    static boolean isValidIsbn13(String isbn) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = isbn.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                return false;
            }
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int check = (10 - sum % 10) % 10;
        return check == (isbn.charAt(12) - '0');
    }
}
