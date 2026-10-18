package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void findFileContentReturnsNullWhenFileMissing() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> response(404, "{}")));

        String content = client.findFileContent(BASE, TOKEN, REPO_PATH, "master", "src/test/java/Missing.java");

        assertEquals(null, content);
    }

    // ========== 分支 HEAD（WP-3.2 幂等闸门与源探查基准） ==========

    @Test
    void branchHeadReturnsCommitId() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> {
            if (url.contains("/repository/branches/")) {
                return response(200, "{\"name\":\"master\",\"commit\":{\"id\":\"a1b2c3d4e5f6\"}}");
            }
            return response(404, "{}");
        }));

        assertEquals("a1b2c3d4e5f6",
                client.branchHead(BASE, TOKEN, REPO_PATH, "master"));
    }

    @Test
    void branchHeadFailsWhenBranchMissing() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> response(404, "{}")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.branchHead(BASE, TOKEN, REPO_PATH, "release"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("分支不存在"));
    }

    @Test
    void branchHeadFailsWhenCommitAbsent() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> {
            if (url.contains("/repository/branches/")) {
                return response(200, "{\"name\":\"master\"}");
            }
            return response(404, "{}");
        }));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.branchHead(BASE, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("HEAD 提交号缺失"));
    }

    // ========== 提交比对（WP-3.4 改名迁移输入） ==========

    @Test
    void compareReturnsDiffEntriesWithFlags() {
        java.util.List<String> seen = new java.util.ArrayList<>();
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> {
            seen.add(url);
            if (url.contains("/repository/compare")) {
                return response(200, """
                        {"diffs":[
                          {"old_path":"src/test/java/a/OldTest.java","new_path":"src/test/java/b/NewTest.java",
                           "new_file":false,"deleted_file":false,"renamed_file":true},
                          {"old_path":"x.java","new_path":"y.java",
                           "new_file":true,"deleted_file":false,"renamed_file":false}
                        ]}
                        """);
            }
            return response(404, "{}");
        }));

        java.util.List<GitLabApiClient.DiffEntry> diffs = client.compare(BASE, TOKEN, REPO_PATH, "oldsha", "newsha");

        assertEquals(2, diffs.size());
        GitLabApiClient.DiffEntry renamed = diffs.get(0);
        assertEquals("src/test/java/a/OldTest.java", renamed.oldPath());
        assertEquals("src/test/java/b/NewTest.java", renamed.newPath());
        assertTrue(renamed.renamedFile());
        assertFalse(renamed.newFile());
        GitLabApiClient.DiffEntry added = diffs.get(1);
        assertTrue(added.newFile());
        assertFalse(added.renamedFile());
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).contains("from=oldsha"));
        assertTrue(seen.get(0).contains("to=newsha"));
    }

    @Test
    void compareFailsWhenUnreachable() {
        GitLabApiClient client = new GitLabApiClient(projectOnly(url -> response(404, "{}")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.compare(BASE, TOKEN, REPO_PATH, "oldsha", "newsha"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("变更比对失败"));
    }

    // ========== 流水线触发（WP-6.1） ==========

    /** 项目探测 + POST 假实现：记录请求以便断言触发报文只含批次票据 */
    private static GitLabApiClient.HttpOpener triggerOpener(java.util.List<String> seenBodies,
            java.util.function.Function<Integer, HttpResponse<String>> postHandler) {
        return new GitLabApiClient.HttpOpener() {
            @Override
            public HttpResponse<String> get(String url, String token) {
                return response(200, "{\"id\":12345}");
            }

            @Override
            public HttpResponse<String> post(String url, String token, String body) {
                seenBodies.add(url + "|" + body);
                return postHandler.apply(url.endsWith("/pipeline") ? 201 : 404);
            }
        };
    }

    @Test
    void triggerPipelineReturnsIidAndVariableOnlyCarriesBatchId() {
        java.util.List<String> seen = new java.util.ArrayList<>();
        GitLabApiClient client = new GitLabApiClient(triggerOpener(seen,
                status -> response(status, "{\"iid\":123456,\"web_url\":\"https://gitlab.example.com/qa/order-api-tests/-/pipelines/123456\",\"status\":\"created\"}")));

        GitLabApiClient.PipelineRef ref = client.triggerPipeline(BASE, TOKEN, REPO_PATH, "master", "3f6a1c9e-8a4c-4d3e-9a1b-0b2c4d5e6f70");

        assertEquals(123456L, ref.iid());
        assertEquals("https://gitlab.example.com/qa/order-api-tests/-/pipelines/123456", ref.webUrl());
        assertEquals("created", ref.status());
        assertEquals(1, seen.size());
        // 详设 4.2 第 1 步：变量只传 ROBOTEST_BATCH_ID，不传场景标识串
        String sent = seen.get(0);
        assertTrue(sent.contains("\"ref\":\"master\""));
        assertTrue(sent.contains("ROBOTEST_BATCH_ID"));
        assertTrue(sent.contains("3f6a1c9e-8a4c-4d3e-9a1b-0b2c4d5e6f70"));
    }

    @Test
    void triggerPipelineFailsWhenGitLabRejects() {
        GitLabApiClient client = new GitLabApiClient(triggerOpener(new java.util.ArrayList<>(),
                status -> response(400, "{\"message\":\"ref is invalid\"}")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.triggerPipeline(BASE, TOKEN, REPO_PATH, "master", "batch-1"));

        assertEquals(1000017704, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 400"));
    }

    @Test
    void triggerPipelineFailsWhenUnreachable() {
        GitLabApiClient client = new GitLabApiClient(new GitLabApiClient.HttpOpener() {
            @Override
            public HttpResponse<String> get(String url, String token) {
                return response(200, "{\"id\":12345}");
            }

            @Override
            public HttpResponse<String> post(String url, String token, String body) throws Exception {
                throw new java.io.IOException("connection refused");
            }
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.triggerPipeline(BASE, TOKEN, REPO_PATH, "master", "batch-1"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("实例不可达"));
    }

    // ========== 流水线状态查询（WP-6.2 轮询兜底） ==========

    /** 项目探测 + GET 假实现：区分项目探测与流水线查询两条 URL */
    private static GitLabApiClient.HttpOpener pipelineOpener(
            java.util.function.Function<String, HttpResponse<String>> pipelineHandler) {
        return (url, token) -> url.contains("/pipelines/")
                ? pipelineHandler.apply(url)
                : response(200, "{\"id\":12345}");
    }

    @Test
    void getPipelineReturnsStatusSnapshot() {
        GitLabApiClient client = new GitLabApiClient(pipelineOpener(
                url -> response(200, "{\"iid\":9,\"web_url\":\"https://gitlab.example.com/p/9\",\"status\":\"running\"}")));

        GitLabApiClient.PipelineStatus status = client.getPipeline(BASE, TOKEN, REPO_PATH, 9L);

        assertEquals(9L, status.iid());
        assertEquals("running", status.status());
        assertEquals("https://gitlab.example.com/p/9", status.webUrl());
    }

    @Test
    void getPipelineMapsHttpErrorToQueryFailed() {
        GitLabApiClient client = new GitLabApiClient(pipelineOpener(url -> response(404, "{}")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getPipeline(BASE, TOKEN, REPO_PATH, 9L));

        assertEquals(1000017705, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 404"));
    }

    @Test
    void getPipelineMapsNetworkErrorToQueryFailed() {
        GitLabApiClient client = new GitLabApiClient((url, token) -> {
            if (url.contains("/pipelines/")) {
                throw new java.io.IOException("connection refused");
            }
            return response(200, "{\"id\":12345}");
        });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getPipeline(BASE, TOKEN, REPO_PATH, 9L));

        // 轮询侧统一按查询失败留痕重试，网络层 17702 归并为 17705
        assertEquals(1000017705, ex.getCode());
    }
}
