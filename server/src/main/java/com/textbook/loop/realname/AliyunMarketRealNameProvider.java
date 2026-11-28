package com.textbook.loop.realname;

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
 * 阿里云云市场「身份证实名认证-二要素」通道（服务商：希恩韦尔）。
 *
 * <pre>
 * POST https://verifytwo.market.alicloudapi.com/identity/verify_id_name?name=张三&amp;id_number=110101199001011237
 * Authorization: APPCODE &lt;appcode&gt;
 *
 * 200 {"charge":1,"result":{"message":"身份证号和姓名不一致","checkresult":2},
 *      "result_code":"0000","result_msg":"调用成功","request_id":"TID..."}
 * </pre>
 *
 * <p>判定：`result_code == "0000"` 表示调用成功，`result.checkresult == 1` 表示「姓名与号码一致」，
 * `2` 表示不一致。AppCode 是凭证，只放服务端配置（gitignored 的 application-identity-local.yml 或环境变量）。</p>
 */
public class AliyunMarketRealNameProvider implements RealNameProvider {

    private static final Logger log = LoggerFactory.getLogger(AliyunMarketRealNameProvider.class);

    private final AppProps props;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public AliyunMarketRealNameProvider(AppProps props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, props.getRealnameTimeoutSeconds())))
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
    public CheckResult check(String realName, String idNo) {
        String url = props.getAliyunMarketIdcheckUrl()
                + "?name=" + URLEncoder.encode(realName, StandardCharsets.UTF_8)
                + "&id_number=" + URLEncoder.encode(idNo, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, props.getRealnameTimeoutSeconds())))
                    .header("Authorization", "APPCODE " + props.getAliyunMarketAppcode())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int http = response.statusCode();
            String text = response.body() == null ? "" : response.body();
            if (http != 200) {
                log.warn("实名核验返回 HTTP {}，body={}", http, abbreviate(text));
                throw BizException.badRequest("实名核验服务暂时不可用（HTTP " + http + "），请稍后重试");
            }
            JsonNode node = mapper.readTree(text);
            String resultCode = node.path("result_code").asText("");
            String resultMsg = node.path("result_msg").asText("");
            if (!"0000".equals(resultCode)) {
                log.warn("实名核验失败 result_code={} msg={}", resultCode, resultMsg);
                throw BizException.badRequest("实名核验失败：" + resultMsg);
            }
            JsonNode result = node.path("result");
            int checkResult = result.path("checkresult").asInt(0);
            String message = result.path("message").asText("");
            boolean passed = checkResult == 1;
            log.info("实名核验完成 checkresult={} message={} request_id={}",
                    checkResult, message, node.path("request_id").asText(""));
            return new CheckResult(passed, passed ? "核验一致" : shortMessage(message, checkResult));
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.badRequest("实名核验被中断，请稍后重试");
        } catch (Exception e) {
            log.warn("实名核验调用异常：{}", e.toString());
            throw BizException.badRequest("实名核验通道暂时不可用，请稍后重试");
        }
    }

    private static String shortMessage(String message, int checkResult) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return checkResult == 2 ? "姓名与身份证号不一致" : "核验未通过（checkresult=" + checkResult + "）";
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
