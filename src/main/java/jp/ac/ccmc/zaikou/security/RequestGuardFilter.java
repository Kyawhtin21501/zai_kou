package jp.ac.ccmc.zaikou.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** A bounded, single-instance login throttle. Forwarded IP headers are deliberately not trusted. */
final class RequestGuardFilter extends OncePerRequestFilter {
    private record Window(long minute, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private synchronized boolean allowed(String address) {
        long minute = Instant.now().getEpochSecond() / 60;
        windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        Window old = windows.get(address);
        if (old == null && windows.size() >= 10000) return false;
        int count = old == null ? 0 : old.count;
        if (count >= 20) return false;
        windows.put(address, new Window(minute, count + 1));
        return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (req.getContentLengthLong() > 16384) { SecurityConfig.jsonError(res,413,"リクエストが大きすぎます。"); return; }
        if ("POST".equals(req.getMethod()) && "/api/auth/login".equals(req.getServletPath()) && !allowed(req.getRemoteAddr())) {
            res.setHeader("Retry-After", "60");
            SecurityConfig.jsonError(res,429,"ログイン試行が多すぎます。1分後に再試行してください。"); return;
        }
        if (req.getContentType() != null && req.getContentType().startsWith("application/json")) {
            byte[] body = req.getInputStream().readNBytes(16385);
            if (body.length > 16384) { SecurityConfig.jsonError(res,413,"リクエストが大きすぎます。"); return; }
            HttpServletRequest wrapped = new HttpServletRequestWrapper(req) {
                @Override public ServletInputStream getInputStream() {
                    ByteArrayInputStream input = new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        @Override public int read() { return input.read(); }
                        @Override public boolean isFinished() { return input.available() == 0; }
                        @Override public boolean isReady() { return true; }
                        @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request only"); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
            };
            chain.doFilter(wrapped, res);
        } else chain.doFilter(req, res);
    }
}
