package com.textbook.loop.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 分页 size 全局兜底。
 *
 * <p>验收审计发现全项目 31 个分页接口的 {@code size} 参数没有上限，客户端传
 * {@code ?size=100000} 就能一次把整表拉出来，既是性能隐患也是数据泄露面。
 * 与其在每个 Controller 里写一遍 {@code Math.min(size, 50)}，这里用请求包装器
 * 统一在参数绑定前把 {@code size} 夹到 {@link #MAX_SIZE}，业务代码不用改。</p>
 */
@Component
public class PageSizeFilter extends OncePerRequestFilter {

    /** 单页最大条数。 */
    public static final long MAX_SIZE = 50;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getParameter("size") == null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new SizeClampRequest(request), response);
    }

    private static String clamp(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            long size = Long.parseLong(raw.trim());
            return size > MAX_SIZE ? String.valueOf(MAX_SIZE) : raw;
        } catch (NumberFormatException ignore) {
            // 非数字交给 Spring 的类型转换去报 40000，这里不动
            return raw;
        }
    }

    private static final class SizeClampRequest extends HttpServletRequestWrapper {

        SizeClampRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return "size".equals(name) ? clamp(value) : value;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (!"size".equals(name) || values == null) {
                return values;
            }
            String[] clamped = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                clamped[i] = clamp(values[i]);
            }
            return clamped;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> source = super.getParameterMap();
            if (!source.containsKey("size")) {
                return source;
            }
            Map<String, String[]> copy = new HashMap<>(source);
            copy.put("size", getParameterValues("size"));
            return copy;
        }
    }
}
