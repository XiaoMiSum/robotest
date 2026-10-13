package io.github.xiaomisum.robotest.framework.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI 只读令牌认证（WP-6.1，GitLab 集成详设 4.6）：fail-closed 与常量时间比对放行。
 */
class CiTokenAuthFilterTest {

    private static final String TOKEN = "ci-token-xyz";

    @Test
    void rejectsWhenHeaderMissingOrMismatched() throws Exception {
        CiTokenAuthFilter filter = new CiTokenAuthFilter(TOKEN);

        MockHttpServletRequest noHeader = new MockHttpServletRequest("GET", "/api/ci/batches/b1/manifest");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(noHeader, denied, new MockFilterChain());
        assertEquals(401, denied.getStatus());

        MockHttpServletRequest wrong = new MockHttpServletRequest("GET", "/api/ci/batches/b1/manifest");
        wrong.addHeader(CiTokenAuthFilter.TOKEN_HEADER, "tampered");
        MockHttpServletResponse denied2 = new MockHttpServletResponse();
        filter.doFilter(wrong, denied2, new MockFilterChain());
        assertEquals(401, denied2.getStatus());
    }

    @Test
    void failClosedWhenPlatformTokenUnconfigured() throws Exception {
        CiTokenAuthFilter filter = new CiTokenAuthFilter("  ");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ci/batches/b1/manifest");
        request.addHeader(CiTokenAuthFilter.TOKEN_HEADER, "anything");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        filter.doFilter(request, denied, new MockFilterChain());

        assertEquals(401, denied.getStatus());
    }

    @Test
    void passesWhenHeaderMatchesConfiguredToken() throws Exception {
        CiTokenAuthFilter filter = new CiTokenAuthFilter(TOKEN);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ci/batches/b1/manifest");
        request.addHeader(CiTokenAuthFilter.TOKEN_HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() == request);
    }
}
