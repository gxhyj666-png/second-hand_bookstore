package com.textbook.loop.isbn;

/**
 * ISBN 图书元数据通道：用 ISBN 查书名/作者/出版社/定价/封面。
 *
 * <p>与 {@code sms}、{@code realname} 同一套 provider 结构：开发用 mock（不联网），
 * 需要真实数据时切到阿里云云市场「ISBN 标准书号查询」。本地教材库命中时优先用本地，不会走这里。</p>
 */
public interface IsbnMetaProvider {

    /** 通道名（local / mock / aliyun-market），写进接口响应的 source 字段 */
    String name();

    boolean mock();

    BookMeta lookup(String isbn);

    /** 一条图书元数据；found=false 时 message 给出原因 */
    record BookMeta(boolean found, String isbn, String title, String author, String publisher,
                    String pubDate, String edition, String price, String coverUrl,
                    String gist, String source, String message) {

        public static BookMeta notFound(String isbn, String source, String message) {
            return new BookMeta(false, isbn, "", "", "", "", "", "", "", "", source, message);
        }
    }
}
