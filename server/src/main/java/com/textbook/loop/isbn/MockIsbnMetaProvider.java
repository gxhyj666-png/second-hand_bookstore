package com.textbook.loop.isbn;

 /**
 * 开发用元数据通道：不联网，永远返回未收录，让调用方走"手填"路径。
 */
public class MockIsbnMetaProvider implements IsbnMetaProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean mock() {
        return true;
    }

    @Override
    public BookMeta lookup(String isbn) {
        return BookMeta.notFound(isbn, name(), "开发模式未接第三方图书库，可手动填写书名与价格后发布");
    }
}
