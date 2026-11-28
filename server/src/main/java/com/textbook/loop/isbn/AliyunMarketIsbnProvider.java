package com.textbook.loop.isbn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.textbook.loop.common.BizException;
import com.textbook.loop.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 阿里云云市场「ISBN 标准书号查询」通道（商品 cmapi00066353）。
 *
 * <p>调用契约（实测）：{@code POST https://lhisbnshcx.market.alicloudapi.com/isbn/query}，
 * header {@code Authorization: APPCODE <AppCode>}，body 用 {@code application/x-www-form-urlencoded}，
 * 只允许一个参数 {@code isbn}（放 JSON 里会报 {@code Parameter `isbn` is required}）。
 * 成功响应 {@code {"success":true,"code":200,"msg":"成功","data":{"details":[{title,author,publisher,pubDate,edition,price,img,gist,...}]}}}。</p>
 */
public class AliyunMarketIsbnProvider implements IsbnMetaProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunMarketIsbnProvider.class);

    private final AppProps props;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public AliyunMarketIsbnProvider(AppProps props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, props.getRealnameTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "aliyun-market";
    }

    @Override
    public boolean mock() {
        return false;
    }

    @Override
    public BookMeta lookup(String isbn) {
        String url = props.getAliyunMarketIsbnUrl();
        String payload = "isbn=" + URLEncoder.encode(isbn, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, props.getRealnameTimeoutSeconds())))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "APPCODE " + props.getAliyunMarketAppcode())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int http = response.statusCode();
            if (http == 400) {
                // 上游对「校验位不合法 / 明显不存在的 ISBN」返回 400，这属于正常业务结果，不当异常抛
                log.info("ISBN 查询被上游拒绝（HTTP 400），按未收录处理：isbn={}", isbn);
                return BookMeta.notFound(isbn, name(), "图书库没有这本书（ISBN 可能不合法），可手动填写书籍信息");
            }
            if (http != 200) {
                String err = response.headers().firstValue("X-Ca-Error-Message").orElse("");
                log.warn("ISBN 查询返回 HTTP {}，{}", http, err);
                throw BizException.badRequest("图书信息查询失败（HTTP " + http + "）"
                        + (err.isBlank() ? "" : "：" + err));
            }
            JsonNode root = mapper.readTree(response.body());
            if (!root.path("success").asBoolean(false)) {
                String msg = root.path("msg").asText("未知错误");
                log.warn("ISBN 查询被拒绝：{}", msg);
                return BookMeta.notFound(isbn, name(), "图书库未收录该 ISBN：" + msg);
            }
            JsonNode details = root.path("data").path("details");
            if (!details.isArray() || details.isEmpty()) {
                return BookMeta.notFound(isbn, name(), "图书库没有这本书的数据，可手动填写");
            }
            JsonNode d = details.get(0);
            BookMeta meta = new BookMeta(true,
                    d.path("isbn").asText(isbn),
                    d.path("title").asText(""),
                    d.path("author").asText(""),
                    d.path("publisher").asText(""),
                    d.path("pubDate").asText(""),
                    d.path("edition").asText(""),
                    d.path("price").asText(""),
                    d.path("img").asText(""),
                    d.path("gist").asText(""),
                    name(),
                    "已从图书库匹配");
            log.info("ISBN 查询成功：isbn={} title={} taskNo={}", isbn, meta.title(), root.path("taskNo").asText(""));
            return meta;
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.badRequest("图书信息查询被中断，请稍后重试");
        } catch (Exception e) {
            log.warn("ISBN 查询调用异常：{}", e.toString());
            throw BizException.badRequest("图书信息查询服务暂时不可用，请稍后重试");
        }
    }
}
