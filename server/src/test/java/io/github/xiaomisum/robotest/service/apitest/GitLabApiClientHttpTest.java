package io.github.xiaomisum.robotest.service.apitest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitLabApiClient 真实 HTTP 级测试：以本地 {@link com.sun.net.httpserver.HttpServer} 假 GitLab
 * 驱动默认构造（真实 java.net.http 链路），锁定 HttpOpener 接缝单测覆盖不到的传输契约——
 * 请求行（URL 编码的项目路径 / 分支段、查询参数）、PRIVATE-TOKEN 认证头、请求体报文与响应 JSON 解析，
 * 以及 HTTP 401 / 404 / 500 到 10 位错误码的映射（GitLab 集成详细设计 4.4）。
 */
class GitLabApiClientHttpTest {

    private static final String TOKEN = "glpat-xxxx";
    private static final String REPO_PATH = "qa/order-api-tests";
    private static final String PROJECT_PROBE_PATH = "/api/v4/projects/qa%2Forder-api-tests";
    private static final String PROJECT_JSON = "{\"id\":12345}";
    private static final byte[] ZIP_BYTES = {'P', 'K', 3, 4, 0, 1, 2, 3};

    /** 已记录的请求：rawPath / rawQuery 保留发送侧原始编码，用于断言请求行 */
    private record Recorded(String method, String rawPath, String rawQuery,
                            String token, String contentType, String body) {
    }

    private record Reply(int status, byte[] body) {
    }

    private HttpServer httpServer;
    private String base;
    private GitLabApiClient client;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private Function<Recorded, Reply> responder;

    @BeforeEach
    void setUp() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Recorded recorded = new Recorded(exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(), exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), body);
            requests.add(recorded);
            Reply reply = responder.apply(recorded);
            if (reply.body().length == 0) {
                exchange.sendResponseHeaders(reply.status(), -1);
            } else {
                exchange.sendResponseHeaders(reply.status(), reply.body().length);
                exchange.getResponseBody().write(reply.body());
            }
            exchange.close();
        });
        httpServer.start();
        base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        client = new GitLabApiClient();
        responder = recorded -> json(404, "{}");
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    private static Reply json(int status, String body) {
        return new Reply(status, body.getBytes(StandardCharsets.UTF_8));
    }

    /** 项目探测统一桩：后续端点（tree / files / compare / pipeline）都以数字项目 ID 拼路径 */
    private Function<Recorded, Reply> withProjectOk(Function<Recorded, Reply> rest) {
        return recorded -> PROJECT_PROBE_PATH.equals(recorded.rawPath())
                ? json(200, PROJECT_JSON) : rest.apply(recorded);
    }

    private Recorded lastRequest() {
        return requests.get(requests.size() - 1);
    }

    private static void assertTokenOnEveryRequest(List<Recorded> requests, String token) {
        assertTrue(requests.stream().allMatch(r -> token.equals(r.token())),
                "全部请求应携带 PRIVATE-TOKEN 认证头：" + requests);
    }

    // ========== 连通性探测 ==========

    @Test
    void probe_sendsEncodedProjectAndBranchPathsWithPrivateTokenHeader() {
        responder = recorded -> switch (recorded.rawPath()) {
            case "/api/v4/version" -> json(200, "{\"version\":\"17.5.1\"}");
            case "/api/v4/personal_access_tokens/self" -> json(200, "{\"scopes\":[\"api\",\"read_repository\"]}");
            case PROJECT_PROBE_PATH -> json(200, PROJECT_JSON);
            case "/api/v4/projects/12345/repository/branches/feature%2Frelease-candidate" ->
                    json(200, "{\"name\":\"feature/release-candidate\",\"commit\":{\"id\":\"a1b2c3d4\"}}");
            default -> json(404, "{}");
        };

        // 基址带尾斜杠：客户端应自行裁剪，不产生 "//" 路径
        GitLabApiClient.Probe probe = client.probe(base + "/", TOKEN, REPO_PATH, "feature/release-candidate");

        assertEquals("17.5.1", probe.version());
        // 分支名按路径段编码（/ → %2F），GitLab 按编码后的段解析分支
        assertEquals("feature/release-candidate", probe.resolvedBranch());
        assertEquals(4, requests.size());
        // 项目路径编码为单个路径段（GitLab v4 API 硬要求）
        assertEquals(PROJECT_PROBE_PATH, requests.get(2).rawPath());
        assertEquals("GET", lastRequest().method());
        assertTokenOnEveryRequest(requests, TOKEN);
    }

    @Test
    void probe_http401OnTokenSelf_mapsTo1000017702TokenInvalid() {
        responder = recorded -> "/api/v4/version".equals(recorded.rawPath())
                ? json(200, "{\"version\":\"17.5.1\"}") : json(401, "{}");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.probe(base, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("令牌无效"));
    }

    @Test
    void probe_http404OnProject_mapsTo1000017702ProjectMissing() {
        responder = recorded -> switch (recorded.rawPath()) {
            case "/api/v4/version" -> json(200, "{\"version\":\"17.5.1\"}");
            case "/api/v4/personal_access_tokens/self" -> json(200, "{\"scopes\":[\"api\"]}");
            default -> json(404, "{}");
        };

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.probe(base, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("项目路径不存在"));
    }

    @Test
    void probe_http500OnVersion_mapsTo1000017702InstanceUnreachable() {
        responder = recorded -> json(500, "{}");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.probe(base, TOKEN, REPO_PATH, "master"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("实例不可达：HTTP 500"));
    }

    // ========== 目录枚举 ==========

    @Test
    void listFiles_encodesQueryParamsAndKeepsBlobsOnly() {
        responder = withProjectOk(recorded -> "/api/v4/projects/12345/repository/tree".equals(recorded.rawPath())
                ? json(200, "[{\"path\":\"src/test/java/FooTest.java\",\"type\":\"blob\"},"
                + "{\"path\":\"src/test/java/sub\",\"type\":\"tree\"}]")
                : json(404, "{}"));

        List<String> paths = client.listFiles(base, TOKEN, REPO_PATH, "feature/x", "src/test/java");

        assertEquals(List.of("src/test/java/FooTest.java"), paths);
        Recorded tree = lastRequest();
        assertEquals("GET", tree.method());
        // 分支与目录均按 URL 查询参数编码（空格 → +，/ → %2F）
        assertTrue(tree.rawQuery().contains("ref=feature%2Fx"), tree.rawQuery());
        assertTrue(tree.rawQuery().contains("path=src%2Ftest%2Fjava"), tree.rawQuery());
        assertTrue(tree.rawQuery().contains("recursive=true"), tree.rawQuery());
        assertTrue(tree.rawQuery().contains("per_page=100&page=1"), tree.rawQuery());
        assertEquals(TOKEN, tree.token());
    }

    // ========== 文件读取 ==========

    @Test
    void findFileContent_encodesFilePathAndBase64DecodesResponse() {
        String base64 = Base64.getEncoder()
                .encodeToString("public class FooTest {}".getBytes(StandardCharsets.UTF_8));
        responder = withProjectOk(recorded -> {
            if (!recorded.rawPath().startsWith("/api/v4/projects/12345/repository/files/")) {
                return json(404, "{}");
            }
            return recorded.rawPath().endsWith("Missing.java")
                    ? json(404, "{}")
                    : json(200, "{\"content\":\"" + base64 + "\",\"encoding\":\"base64\"}");
        });

        String content = client.findFileContent(base, TOKEN, REPO_PATH, "master", "src/test/java/FooTest.java");

        assertEquals("public class FooTest {}", content);
        assertEquals("/api/v4/projects/12345/repository/files/src%2Ftest%2Fjava%2FFooTest.java",
                lastRequest().rawPath());
        assertEquals("ref=master", lastRequest().rawQuery());
        assertNull(client.findFileContent(base, TOKEN, REPO_PATH, "master", "src/test/java/Missing.java"));
    }

    @Test
    void getFileContent_http404_mapsTo1000017702FileMissing() {
        responder = withProjectOk(recorded -> json(404, "{}"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getFileContent(base, TOKEN, REPO_PATH, "master", "src/test/java/Missing.java"));

        assertEquals(1000017702, ex.getCode());
        assertTrue(ex.getMessage().contains("文件不存在"));
    }

    // ========== 提交比对 ==========

    @Test
    void compare_sendsEncodedFromToQueryAndParsesDiffs() {
        responder = withProjectOk(recorded -> "/api/v4/projects/12345/repository/compare".equals(recorded.rawPath())
                ? json(200, "{\"diffs\":[{\"old_path\":\"a.java\",\"new_path\":\"b.java\","
                + "\"new_file\":false,\"deleted_file\":false,\"renamed_file\":true}]}")
                : json(404, "{}"));

        List<GitLabApiClient.DiffEntry> diffs = client.compare(base, TOKEN, REPO_PATH, "old sha", "new-sha");

        assertEquals(1, diffs.size());
        assertTrue(diffs.get(0).renamedFile());
        assertTrue(lastRequest().rawQuery().contains("from=old+sha"), lastRequest().rawQuery());
        assertTrue(lastRequest().rawQuery().contains("to=new-sha"), lastRequest().rawQuery());
    }

    // ========== 流水线触发 ==========

    @Test
    void triggerPipeline_postsJsonWithBatchTicketOnly() {
        String batchId = "3f6a1c9e-8a4c-4d3e-9a1b-0b2c4d5e6f70";
        responder = withProjectOk(recorded -> "/api/v4/projects/12345/pipeline".equals(recorded.rawPath())
                ? json(201, "{\"iid\":77,\"web_url\":\"https://gitlab.example.com/p/77\",\"status\":\"created\"}")
                : json(404, "{}"));

        GitLabApiClient.PipelineRef ref = client.triggerPipeline(base, TOKEN, REPO_PATH, "master", batchId);

        assertEquals(77L, ref.iid());
        assertEquals("https://gitlab.example.com/p/77", ref.webUrl());
        assertEquals("created", ref.status());

        Recorded post = lastRequest();
        assertEquals("POST", post.method());
        assertTrue(post.contentType().contains("application/json"), post.contentType());
        assertEquals(TOKEN, post.token());
        // 详设 4.2 第 1 步：变量只传 ROBOTEST_BATCH_ID 批次票据，不传场景标识串
        assertTrue(post.body().contains("\"ref\":\"master\""), post.body());
        assertTrue(post.body().contains("\"key\":\"ROBOTEST_BATCH_ID\""), post.body());
        assertTrue(post.body().contains(batchId), post.body());
    }

    @Test
    void triggerPipeline_http400_mapsTo1000017704() {
        responder = withProjectOk(recorded -> "/api/v4/projects/12345/pipeline".equals(recorded.rawPath())
                ? json(400, "{\"message\":\"ref is invalid\"}") : json(404, "{}"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.triggerPipeline(base, TOKEN, REPO_PATH, "master", "batch-1"));

        assertEquals(1000017704, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 400"));
    }

    // ========== 流水线状态查询 ==========

    @Test
    void getPipeline_returnsStatusSnapshot() {
        responder = withProjectOk(recorded ->
                json(200, "{\"iid\":9,\"web_url\":\"https://gitlab.example.com/p/9\",\"status\":\"running\"}"));

        GitLabApiClient.PipelineStatus status = client.getPipeline(base, TOKEN, REPO_PATH, 9L);

        assertEquals(9L, status.iid());
        assertEquals("running", status.status());
        assertEquals("/api/v4/projects/12345/pipelines/9", lastRequest().rawPath());
    }

    @Test
    void getPipeline_http404_mapsTo1000017705() {
        responder = withProjectOk(recorded -> json(404, "{}"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getPipeline(base, TOKEN, REPO_PATH, 9L));

        assertEquals(1000017705, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 404"));
    }

    @Test
    void getPipeline_http500_mapsTo1000017705() {
        responder = withProjectOk(recorded -> json(500, "{}"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.getPipeline(base, TOKEN, REPO_PATH, 9L));

        assertEquals(1000017705, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

    // ========== 制品下载 ==========

    @Test
    void downloadPipelineArtifacts_returnsZipBytesWithTokenHeader() {
        responder = withProjectOk(recorded ->
                "/api/v4/projects/12345/pipelines/9/artifacts".equals(recorded.rawPath())
                        ? new Reply(200, ZIP_BYTES) : json(404, "{}"));

        byte[] bytes = client.downloadPipelineArtifacts(base, TOKEN, REPO_PATH, 9L);

        assertArrayEquals(ZIP_BYTES, bytes);
        Recorded download = lastRequest();
        assertEquals("GET", download.method());
        assertEquals("/api/v4/projects/12345/pipelines/9/artifacts", download.rawPath());
        assertEquals(TOKEN, download.token());
    }

    @Test
    void downloadPipelineArtifacts_http404_returnsNull() {
        responder = withProjectOk(recorded -> new Reply(404, new byte[0]));

        assertNull(client.downloadPipelineArtifacts(base, TOKEN, REPO_PATH, 9L));
    }

    @Test
    void downloadPipelineArtifacts_http401_mapsTo1000017714() {
        responder = withProjectOk(recorded -> new Reply(401, new byte[0]));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.downloadPipelineArtifacts(base, TOKEN, REPO_PATH, 9L));

        assertEquals(1000017714, ex.getCode());
        assertTrue(ex.getMessage().contains("令牌无效"));
    }

    @Test
    void downloadPipelineArtifacts_http500_mapsTo1000017714() {
        responder = withProjectOk(recorded -> new Reply(500, new byte[0]));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.downloadPipelineArtifacts(base, TOKEN, REPO_PATH, 9L));

        assertEquals(1000017714, ex.getCode());
        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

    @Test
    void downloadPipelineArtifacts_nonZipBody_mapsTo1000017714() {
        // GitLab 无制品时可能以 200 返回非 zip 内容，客户端按 zip 魔数兜底判定
        responder = withProjectOk(recorded ->
                json(200, "<html>error</html>"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> client.downloadPipelineArtifacts(base, TOKEN, REPO_PATH, 9L));

        assertEquals(1000017714, ex.getCode());
        assertTrue(ex.getMessage().contains("zip"));
    }
}
