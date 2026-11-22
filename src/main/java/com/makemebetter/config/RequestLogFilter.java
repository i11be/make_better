package com.makemebetter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求日志：每处理完一个请求，控制台多一行
 *
 * <pre>
 *   GET /album → 200 · 12ms
 *   POST /album/move → 302 · 8ms
 * </pre>
 *
 * 开关是 {@code app.request-log}（application.yml 里默认 true，不想看就改 false）。
 *
 * <h2>为什么这么写（看懂这几条，过滤器就差不多会了）</h2>
 * <ol>
 *   <li><b>Filter 是什么</b>：Servlet 容器里的一层拦截器，请求进 Controller 之前、响应出去之前都会经过它。
 *       跟拦截器（Interceptor）的区别：Filter 更靠外，能拿到原始的 request/response，也管静态资源。</li>
 *   <li><b>为什么继承 OncePerRequestFilter</b>：Spring 提供的基础类，保证同一个请求只进一次
 *       （转发、include 不会重复打日志），比自己实现 Filter 省事。</li>
 *   <li><b>为什么用 finally</b>：Controller 抛异常时也要把这一行打出来，否则"出错的那次请求"在日志里会消失，
 *       排查时最需要它。</li>
 *   <li><b>为什么用 nanoTime</b>：{@code System.currentTimeMillis()} 会随系统时间调整而跳变，
 *       量耗时要用单调时钟 {@code System.nanoTime()}，再换算成毫秒。</li>
 *   <li><b>为什么跳过静态资源</b>：一个页面会连带请求 css/js/图片，全记下来日志就没法看了。</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "app", name = "request-log", havingValue = "true")
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    /** 这些前缀的请求不记：静态资源、图片字节流 */
    private static final String[] SKIP = {"/css/", "/js/", "/img/", "/files/", "/favicon"};

    /** 超过这个毫秒数就当"慢"，用 WARN 打出来提醒一下 */
    private static final long SLOW_MS = 500;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : SKIP) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            // 交给后面的过滤器和 Controller
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String query = request.getQueryString();
            String path = request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
            if (ms >= SLOW_MS) {
                log.warn("{} {} → {} · {}ms（有点慢）", request.getMethod(), path, response.getStatus(), ms);
            } else {
                log.info("{} {} → {} · {}ms", request.getMethod(), path, response.getStatus(), ms);
            }
        }
    }
}
