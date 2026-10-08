package jp.ac.ccmc.zaikou.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class RequestGuardFilterTests {
    @Test void boundsLoginAttemptsWithoutTrustingForwardedAddresses() throws Exception {
        var filter = new RequestGuardFilter();
        for (int i=0; i<21; i++) {
            var req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setServletPath("/api/auth/login"); req.setRemoteAddr("127.0.0.1");
            req.addHeader("X-Forwarded-For", "192.0.2." + i);
            var res = new MockHttpServletResponse();
            filter.doFilter(req,res,new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(i < 20 ? 200 : 429);
        }
    }
    @Test void rejectsOversizedChunkedJsonAndPreservesValidBody() throws Exception {
        var filter = new RequestGuardFilter();
        var large = new MockHttpServletRequest("POST", "/api/items") {
            @Override public long getContentLengthLong() { return -1; }
        };
        large.setContentType("application/json"); large.setContent("x".repeat(16385).getBytes(StandardCharsets.UTF_8));
        var res = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(large,res,chain);
        assertThat(res.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
        var small = new MockHttpServletRequest("POST", "/api/items");
        small.setContentType("application/json"); small.setContent("{\"name\":\"商品\"}".getBytes(StandardCharsets.UTF_8));
        var validChain = new MockFilterChain();
        filter.doFilter(small,new MockHttpServletResponse(),validChain);
        var forwarded = (HttpServletRequest) validChain.getRequest();
        assertThat(forwarded.getReader().readLine()).isEqualTo("{\"name\":\"商品\"}");
    }
}
