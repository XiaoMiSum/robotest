package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitLabApiClient 可测接缝测试：以假 HttpOpener 替代真实网络，覆盖探测顺序与失败原因区分（BR-1.5）。
 */
class GitLabApiClientTest {

    private static final String BASE = "https://gitlab.example.com";
    private static final String TOKEN = "glpat-xxxx";
    private static final String REPO_PATH = "qa/order-api-tests";

    private static HttpResponse<String> response(int status, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        org.mockito.Mockito.when(resp.statusCode()).thenReturn(status);
        org.mockito.Mockito.when(resp.body()).thenReturn(body);
        return resp;
    }

    private final GitLabApiClient client = new GitLabApiClient((url, token) -> {
        if (url.endsWith("/api/v4/version")) {
            return response(200, "{\"version\":\"17.5.1\"}");
        }
        if (url.endsWith("/personal_access_tokens/self")) {
            return response(200, "{\"scopes\":[\"api\",\"read_repository\"]}");
        }
        if (url.contains("/repository/branches/")) {
            return response(200, "{\"name\":\"master\"}");
        }
        if (url.contains("/projects/")) {
            return response(200, "{\"id\":12345}");
        }
        return response(404, "{}");
    });

    @Test
    void probeReturnsVersionAndResolvedBranch() {
        GitLabApiClient.Probe probe = client.probe(BASE + "/", TOKEN, REPO_PATH, "master");

        assertEquals("17.5.1", probe.version());
        assertEquals("master", probe.resolvedBranch());
    }

    @Test
    void probeFailsWhenNetworkError() {
        GitLabApiClient broken = new GitLabApiClient((url, token) -> {
            throw new java.io.IOException("connection refused");
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> broken.probe(BASE, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("实例不可达"));
    }

    @Test
    void probeFailsWhenTokenInvalid() {
        GitLabApiClient invalidToken = new GitLabApiClient((url, token) -> {
            if (url.endsWith("/personal_access_tokens/self")) {
                return response(401, "{}");
            }
            return response(200, "{\"version\":\"17.5.1\"}");
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> invalidToken.probe(BASE, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("令牌无效"));
    }

    @Test
    void probeFailsWhenScopeMissing() {
        GitLabApiClient noScope = new GitLabApiClient((url, token) -> {
            if (url.endsWith("/personal_access_tokens/self")) {
                return response(200, "{\"scopes\":[\"read_repository\"]}");
            }
            return response(200, "{\"version\":\"17.5.1\"}");
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> noScope.probe(BASE, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("api scope"));
    }

    @Test
    void probeFailsWhenBranchMissing() {
        GitLabApiClient noBranch = new GitLabApiClient((url, token) -> {
            if (url.endsWith("/api/v4/version")) {
                return response(200, "{\"version\":\"17.5.1\"}");
            }
            if (url.endsWith("/personal_access_tokens/self")) {
                return response(200, "{\"scopes\":[\"api\"]}");
            }
            if (url.contains("/repository/branches/")) {
                return response(404, "{}");
            }
            return response(200, "{\"id\":12345}");
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> noBranch.probe(BASE, TOKEN, REPO_PATH, "release"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("分支不存在"));
    }

    @Test
    void probeFailsWhenProjectMissing() {
        GitLabApiClient noProject = new GitLabApiClient((url, token) -> {
            if (url.endsWith("/api/v4/version")) {
                return response(200, "{\"version\":\"17.5.1\"}");
            }
            if (url.endsWith("/personal_access_tokens/self")) {
                return response(200, "{\"scopes\":[\"api\"]}");
            }
            return response(404, "{}");
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> noProject.probe(BASE, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("项目路径不存在"));
    }

    @Test
    void probeFailsWhenInstanceUnreachable() {
        GitLabApiClient down = new GitLabApiClient((url, token) -> response(502, "Bad Gateway"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> down.probe(BASE, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("实例不可达"));
    }
}
