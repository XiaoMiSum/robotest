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

    // ========== 目录枚举与文件读取（WP-3.1 扫描输入） ==========

    /** 项目探测假实现：供 listFiles / getFileContent 跳过完整 probe 序列 */
    private static GitLabApiClient.HttpOpener projectOnly(java.util.function.Function<String, HttpResponse<String>> handler) {
        return (url, token) -> {
            if (url.startsWith(BASE + "/api/v4/projects/") && !url.contains("/repository/")) {
                return response(200, "{\"id\":12345}");
            }
            return handler.apply(url);
        };
    }

    @Test
    void listFilesReturnsBlobPathsAndFiltersTrees() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> {
            if (url.contains("/repository/tree") && url.endsWith("&page=1")) {
                return response(200, """
                        [{"path":"src/test/java/FooTest.java","type":"blob"},
                         {"path":"src/test/java/sub","type":"tree"}]
                        """);
            }
            return response(200, "[]");
        }));

        java.util.List<String> paths = client.listFiles(BASE, TOKEN, REPO_PATH, "master", "src/test/java");

        assertEquals(java.util.List.of("src/test/java/FooTest.java"), paths);
    }

    @Test
    void listFilesFollowsPaginationUntilShortPage() {
        StringBuilder page1 = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            page1.append(i > 0 ? "," : "").append("{\"path\":\"f").append(i).append(".java\",\"type\":\"blob\"}");
        }
        page1.append("]");
        // 注意用 "&page=N" 精确匹配，"page=1" 会误匹配 per_page=100 的子串
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> {
            if (url.endsWith("&page=1")) {
                return response(200, page1.toString());
            }
            if (url.endsWith("&page=2")) {
                return response(200, "[{\"path\":\"f100.java\",\"type\":\"blob\"}]");
            }
            return response(200, "[]");
        }));

        java.util.List<String> paths = client.listFiles(BASE, TOKEN, REPO_PATH, "master", "src/test/java");

        assertEquals(101, paths.size());
        assertEquals("f100.java", paths.get(100));
    }

    @Test
    void listFilesFailsWhenTreeUnreachable() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> response(404, "{}")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.listFiles(BASE, TOKEN, REPO_PATH, "master", "src/test/java"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("目录不可达"));
    }

    @Test
    void getFileContentDecodesBase64() {
        String base64 = java.util.Base64.getEncoder()
                .encodeToString("public class FooTest {}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> {
            if (url.contains("/repository/files/")) {
                return response(200, "{\"content\":\"" + base64 + "\",\"encoding\":\"base64\"}");
            }
            return response(404, "{}");
        }));

        String content = client.getFileContent(BASE, TOKEN, REPO_PATH, "master", "src/test/java/FooTest.java");

        assertEquals("public class FooTest {}", content);
    }

    @Test
    void getFileContentFailsWhenFileMissing() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> response(404, "{}")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getFileContent(BASE, TOKEN, REPO_PATH, "master", "src/test/java/Missing.java"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("文件不存在"));
    }
}
