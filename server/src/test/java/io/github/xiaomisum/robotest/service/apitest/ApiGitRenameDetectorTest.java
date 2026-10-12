package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.service.apitest.scan.JavaParserTestSourceScanner;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedClass;
import io.github.xiaomisum.robotest.service.apitest.scan.ScannedMethod;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改名探查与内容校验测试（详设 3.5 规则 1/2/5）：候选对过滤、重合度采信与静默退回、真实错误上抛。
 */
class ApiGitRenameDetectorTest {

    private static final String BASE = "https://gitlab.example.com";
    private static final String TOKEN = "glpat-xxxx";
    private static final String REPO = "qa/order-api-tests";
    private static final String FROM = "oldsha123";
    private static final String OLD_PATH = "src/test/java/com/example/a/OldFlowTest.java";
    private static final String NEW_PATH = "src/test/java/com/example/b/FlowOpsTest.java";
    private static final String SOURCE_DIR = "src/test/java";

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> resp = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        org.mockito.Mockito.when(resp.statusCode()).thenReturn(status);
        org.mockito.Mockito.when(resp.body()).thenReturn(body);
        return resp;
    }

    /** 项目探测 + 按 URL 分发：compare / 旧版文件读取 */
    private static GitLabApiClient client(java.util.function.Function<String, HttpResponse<String>> handler) {
        return new GitLabApiClient((url, token) -> {
            if (url.startsWith(BASE + "/api/v4/projects/") && !url.contains("/repository/")) {
                return response(200, "{\"id\":12345}");
            }
            return handler.apply(url);
        });
    }

    private static String base64(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    private static ApiGitRenameDetector detector(GitLabApiClient httpClient) {
        return new ApiGitRenameDetector(httpClient, new JavaParserTestSourceScanner());
    }

    /** 项目探测 + 旧版文件读取：oldSource 为 null 模拟文件在 from 提交下缺失（404） */
    private static ApiGitRenameDetector detectorWithOldContent(String oldSource) {
        return detector(client(url -> {
            if (oldSource != null && url.contains("/repository/files/") && url.contains("ref=" + FROM)) {
                return response(200, "{\"content\":\"" + base64(oldSource) + "\",\"encoding\":\"base64\"}");
            }
            return response(404, "{}");
        }));
    }

    // ========== compare 候选对过滤 ==========

    @Test
    void comparePairsKeepsRenamedJavaUnderSourceDir() {
        GitLabApiClient httpClient = client(url -> {
            if (url.contains("/repository/compare")) {
                return response(200, """
                        {"diffs":[
                          {"old_path":"src/test/java/a/OldTest.java","new_path":"src/test/java/a/NewTest.java",
                           "new_file":false,"deleted_file":false,"renamed_file":true},
                          {"old_path":"other/OldTest.java","new_path":"other/NewTest.java",
                           "new_file":false,"deleted_file":false,"renamed_file":true},
                          {"old_path":"src/test/java/a/README.md","new_path":"src/test/java/a/NOTE.md",
                           "new_file":false,"deleted_file":false,"renamed_file":true},
                          {"old_path":"src/test/java/a/Add.java","new_path":"src/test/java/a/Add.java",
                           "new_file":true,"deleted_file":false,"renamed_file":false}
                        ]}
                        """);
            }
            return response(404, "{}");
        });

        List<ApiGitRenameDetector.RenamePair> pairs = detector(httpClient)
                .comparePairs(BASE, TOKEN, REPO, FROM, "newsha456", SOURCE_DIR);

        assertEquals(1, pairs.size());
        assertEquals("src/test/java/a/OldTest.java", pairs.get(0).oldPath());
        assertEquals("src/test/java/a/NewTest.java", pairs.get(0).newPath());
    }

    // ========== 内容校验（重合度） ==========

    @Test
    void validateAcceptsWhenMethodSetsOverlap() {
        String oldSource = """
                package com.example.a;

                public class OldFlowTest {
                    @Test(description = "下单流程")
                    @RyzeTest("order/place.yaml")
                    public void testPlace(TestElement<?> el) { }

                    @Test(description = "支付流程")
                    @RyzeTest("order/pay.yaml")
                    public void testPay(TestElement<?> el) { }
                }
                """;
        ScannedClass newClass = new ScannedClass(NEW_PATH, "com.example.b", "FlowOpsTest", List.of(
                new ScannedMethod("testPlace", "下单流程", "order/place.yaml"),
                new ScannedMethod("testPay", "支付流程", "order/pay.yaml")));

        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(oldSource)
                .validatePairs(BASE, TOKEN, REPO, FROM,
                        List.of(new ApiGitRenameDetector.RenamePair(OLD_PATH, NEW_PATH)), List.of(newClass));

        assertEquals(Map.of("com.example.a.OldFlowTest", "com.example.b.FlowOpsTest"), result.classRenames());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateAcceptsWhenTemplateRefsOverlapOnly() {
        String oldSource = """
                package com.example.a;

                public class OldFlowTest {
                    @Test(description = "旧用例一")
                    @RyzeTest("shared/first.yaml")
                    public void testAlpha(TestElement<?> el) { }

                    @Test(description = "旧用例二")
                    @RyzeTest("shared/second.yaml")
                    public void testBeta(TestElement<?> el) { }
                }
                """;
        // 方法集合完全不同，但步骤模板引用集合一致 → 按规则 2 的「或」语义采信
        ScannedClass newClass = new ScannedClass("src/test/java/com/example/a/NewFlowOpsTest.java",
                "com.example.a", "NewFlowOpsTest", List.of(
                new ScannedMethod("testGamma", "新用例一", "shared/first.yaml"),
                new ScannedMethod("testDelta", "新用例二", "shared/second.yaml")));

        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(oldSource)
                .validatePairs(BASE, TOKEN, REPO, FROM,
                        List.of(new ApiGitRenameDetector.RenamePair(OLD_PATH,
                                "src/test/java/com/example/a/NewFlowOpsTest.java")), List.of(newClass));

        assertEquals(Map.of("com.example.a.OldFlowTest", "com.example.a.NewFlowOpsTest"), result.classRenames());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateRejectsSilentlyWhenOverlapBelowThreshold() {
        String oldSource = """
                package com.example.a;

                public class OldFlowTest {
                    @Test(description = "下单流程")
                    @RyzeTest("order/place.yaml")
                    public void testPlace(TestElement<?> el) { }

                    @Test(description = "支付流程")
                    @RyzeTest("order/pay.yaml")
                    public void testPay(TestElement<?> el) { }
                }
                """;
        // 方法集合与模板引用集合均无交集 → 不采信且不计错误（静默退回删旧建新）
        ScannedClass newClass = new ScannedClass(NEW_PATH, "com.example.b", "FlowOpsTest", List.of(
                new ScannedMethod("testRenamedA", "完全无关用例甲", "misc/other_a.yaml"),
                new ScannedMethod("testRenamedB", "完全无关用例乙", "misc/other_b.yaml")));

        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(oldSource)
                .validatePairs(BASE, TOKEN, REPO, FROM,
                        List.of(new ApiGitRenameDetector.RenamePair(OLD_PATH, NEW_PATH)), List.of(newClass));

        assertTrue(result.classRenames().isEmpty());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateSkipsSilentlyWhenClassNameDiffersFromFileName() {
        // 文件名为 OldFlowTest.java 但类声明为 SomethingElse（非公开类改名等无法探查情形，BR-1.42）
        String oldSource = """
                package com.example.a;

                class SomethingElse {
                    @Test(description = "下单流程")
                    @RyzeTest("order/place.yaml")
                    public void testPlace(TestElement<?> el) { }

                    @Test(description = "支付流程")
                    @RyzeTest("order/pay.yaml")
                    public void testPay(TestElement<?> el) { }
                }
                """;
        ScannedClass newClass = new ScannedClass(NEW_PATH, "com.example.b", "FlowOpsTest", List.of(
                new ScannedMethod("testPlace", "下单流程", "order/place.yaml"),
                new ScannedMethod("testPay", "支付流程", "order/pay.yaml")));

        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(oldSource)
                .validatePairs(BASE, TOKEN, REPO, FROM,
                        List.of(new ApiGitRenameDetector.RenamePair(OLD_PATH, NEW_PATH)), List.of(newClass));

        assertTrue(result.classRenames().isEmpty());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateSkipsSilentlyWhenNewSideNotTestedClass() {
        String oldSource = """
                package com.example.a;

                public class OldFlowTest {
                    @Test(description = "下单流程")
                    @RyzeTest("order/place.yaml")
                    public void testPlace(TestElement<?> el) { }
                }
                """;
        // 新侧扫描清单中不存在该路径（新文件不是测试类）→ 无迁移对象，静默
        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(oldSource)
                .validatePairs(BASE, TOKEN, REPO, FROM,
                        List.of(new ApiGitRenameDetector.RenamePair(OLD_PATH, NEW_PATH)), List.of());

        assertTrue(result.classRenames().isEmpty());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateRecordsErrorWhenOldFileMissing() {
        // 新侧已是测试类但旧版文件在 from 提交下不可读 → 真实错误明细（导入侧计 errorFailed），迁移降级
        ScannedClass newClass = new ScannedClass(NEW_PATH, "com.example.b", "FlowOpsTest", List.of(
                new ScannedMethod("testPlace", "下单流程", "order/place.yaml")));
        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(null)
                .validatePairs(BASE, TOKEN, REPO, FROM,
                        List.of(new ApiGitRenameDetector.RenamePair(OLD_PATH, NEW_PATH)), List.of(newClass));

        assertTrue(result.classRenames().isEmpty());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("旧版本文件不存在"));
    }

    @Test
    void validateReturnsEmptyWhenNoPairs() {
        ApiGitRenameDetector.RenameResult result = detectorWithOldContent(null)
                .validatePairs(BASE, TOKEN, REPO, FROM, List.of(), List.of());

        assertTrue(result.classRenames().isEmpty());
        assertTrue(result.errors().isEmpty());
    }
}
