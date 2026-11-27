package com.textbook.loop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.textbook.loop.domain.SysTextbook;
import com.textbook.loop.mapper.SysTextbookMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ISBN 扫码获取书籍信息。
 *
 * <p>数据来源优先级：本地教材库（ISBN-13 精确匹配）→ 三方图书 API。
 * 教学演示环境不依赖外部网络，本地库没有时返回 found=false，由前端引导手填或换一种方式录入。</p>
 */
@Service
public class IsbnService {

    private final SysTextbookMapper textbookMapper;

    public IsbnService(SysTextbookMapper textbookMapper) {
        this.textbookMapper = textbookMapper;
    }

    public Map<String, Object> lookup(String isbn) {
        String clean = isbn == null ? "" : isbn.replaceAll("[^0-9Xx]", "");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("isbn", clean);
        if (clean.length() != 10 && clean.length() != 13) {
            map.put("found", false);
            map.put("message", "ISBN 应为 10 位或 13 位");
            return map;
        }
        SysTextbook book = textbookMapper.selectOne(new LambdaQueryWrapper<SysTextbook>()
                .eq(SysTextbook::getIsbn13, clean).last("limit 1"));
        if (book == null) {
            map.put("found", false);
            map.put("message", "本地教材库未收录，可手动填写书名与价格后发布");
            return map;
        }
        map.put("found", true);
        map.put("textbookId", book.getId());
        map.put("title", book.getTitle());
        map.put("author", book.getAuthor());
        map.put("publisher", book.getPublisher());
        map.put("edition", book.getEdition());
        map.put("price", book.getPrice());
        map.put("category", book.getCategory());
        return map;
    }
}
