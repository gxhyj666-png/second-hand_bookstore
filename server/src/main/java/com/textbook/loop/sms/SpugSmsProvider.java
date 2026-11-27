package com.textbook.loop.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.textbook.loop.common.BizException;
import com.textbook.loop.config.AppProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spug 推送助手（https://push.spug.cc）短信验证码通道。
 *
 * <p>接口：{@code POST {base}/sms/<TEMPLATE_CODE>}，body {@code {"to":"手机号","code":"验证码"}}，
 * 返回 {@code {"code":200,"msg":"请求成功","request_id":"..."}}。TEMPLATE_CODE 就是凭证，
 * 在控制台「短信验证码」模板详情里复制完整调用 URL 得到；官方模板内容不可修改，无需申请签名。</p>
 */
public class SpugSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(SpugSmsProvider.class);

    private final AppProps props;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public SpugSmsProvider(AppProps props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, props.getSpugTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "spug";
    }

    @Override
    public boolean mock() {
        return false;
    }

    @Override
    public void sendCode(String phone, String code, int ttlSeconds) {
        String base = trimEnd(props.getSpugBaseUrl());
        String url = base + "/sms/" + props.getSpugTemplateCode();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", phone);
        body.put("code", code);
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, props.getSpugTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int http = response.statusCode();
            String text = response.body() == null ? "" : response.body();
            if (http < 200 || http >= 300) {
                log.warn("Spug 短信返回 HTTP {}，body={}", http, abbreviate(text));
                throw BizException.badRequest("短信发送失败（HTTP " + http + "），请稍后重试");
            }
            JsonNode node = mapper.readTree(text);
            int code200 = node.path("code").asInt(-1);
            if (code200 != 200) {
                String msg = node.path("msg").asText("未知错误");
                log.warn("Spug 短信发送被拒绝：{}，body={}", msg, abbreviate(text));
                throw BizException.badRequest("短信发送失败：" + msg);
            }
            log.info("Spug 短信已提交：to={} request_id={}", mask(phone), node.path("request_id").asText(""));
        } catch (BizException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.badRequest("短信发送被中断，请稍后重试");
        } catch (Exception e) {
            log.warn("Spug 短信调用异常：{}", e.toString());
            throw BizException.badRequest("短信通道暂时不可用，请稍后重试");
        }
    }

    private static String trimEnd(String s) {
        if (s == null || s.isBlank()) {
            return "https://push.spug.cc";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String abbreviate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
