package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 远程套件 YAML 加载器测试：!include 内联（资源根相对路径）、会话缓存、防环与解析失败口径（BR-1.9）。
 */
class RyzeSuiteYamlLoaderTest {

    private static RyzeSuiteYamlLoader loader(Map<String, String> files) {
        return new RyzeSuiteYamlLoader(files::get);
    }

    @Test
    void loadSuiteInlinesIncludesAsParsedContent() {
        Map<String, String> files = Map.of(
                "order/list/query.yaml", """
                        title: 查询订单状态
                        variables:
                          status: paid
                        configelements: !include 'common/env/env.yaml'
                        preprocessors:
                          - !include 'common/processor/login.yaml'
                        children:
                          - title: 查询
                            testclass: http
                        """,
                "common/env/env.yaml", """
                        - testclass: http
                          config:
                            host: example.com
                        """,
                "common/processor/login.yaml", """
                        title: 登录
                        testclass: http
                        """);

        Map<String, Object> suite = loader(files).loadSuite("order/list/query.yaml");

        assertEquals("查询订单状态", suite.get("title"));
        // include 内联为解析后的文档根：env.yaml 根为列表、login.yaml 根为映射
        assertInstanceOf(List.class, suite.get("configelements"));
        List<?> env = (List<?>) suite.get("configelements");
        assertEquals("http", ((Map<?, ?>) env.get(0)).get("testclass"));
        assertInstanceOf(List.class, suite.get("preprocessors"));
        List<?> preprocessors = (List<?>) suite.get("preprocessors");
        assertEquals("登录", ((Map<?, ?>) preprocessors.get(0)).get("title"));
        // children 无 include，原样解析
        assertInstanceOf(List.class, suite.get("children"));
    }

    @Test
    void includeTextFetchedOncePerSession() {
        AtomicInteger fetchCount = new AtomicInteger();
        Map<String, String> files = new HashMap<>();
        files.put("a.yaml", "title: A\nconfigelements: !include 'common/env/env.yaml'");
        files.put("b.yaml", "title: B\nconfigelements: !include 'common/env/env.yaml'");
        files.put("common/env/env.yaml", "- testclass: http");
        RyzeSuiteYamlLoader shared = new RyzeSuiteYamlLoader(path -> {
            fetchCount.incrementAndGet();
            return files.get(path);
        });

        shared.loadSuite("a.yaml");
        shared.loadSuite("b.yaml");

        // env.yaml 被两个套件引用，文本只拉取一次
        assertEquals(3, fetchCount.get());
    }

    @Test
    void missingRootTemplateReportsParseFailure() {
        RyzeSuiteYamlLoader loader = loader(Map.of());

        RyzeSuiteYamlLoader.LoadException ex = assertThrows(RyzeSuiteYamlLoader.LoadException.class,
                () -> loader.loadSuite("order/list/absent.yaml"));

        assertTrue(ex.getMessage().contains("步骤模板"));
        assertTrue(ex.getMessage().contains("未找到"));
    }

    @Test
    void missingIncludeReportsParseFailure() {
        Map<String, String> files = Map.of("a.yaml", "title: A\nconfigelements: !include 'common/env/absent.yaml'");

        RyzeSuiteYamlLoader.LoadException ex = assertThrows(RyzeSuiteYamlLoader.LoadException.class,
                () -> loader(files).loadSuite("a.yaml"));

        assertTrue(ex.getMessage().contains("引用文件"));
        assertTrue(ex.getMessage().contains("common/env/absent.yaml"));
    }

    @Test
    void circularIncludeRejected() {
        Map<String, String> files = Map.of(
                "a.yaml", "title: A\npreprocessors:\n  - !include 'b.yaml'",
                "b.yaml", "title: B\npreprocessors:\n  - !include 'a.yaml'");

        RyzeSuiteYamlLoader.LoadException ex = assertThrows(RyzeSuiteYamlLoader.LoadException.class,
                () -> loader(files).loadSuite("a.yaml"));

        assertTrue(ex.getMessage().contains("引用成环"));
    }

    @Test
    void siblingDuplicateIncludeAllowed() {
        Map<String, String> files = Map.of(
                "a.yaml", """
                        title: A
                        preprocessors:
                          - !include 'common/x.yaml'
                          - !include 'common/x.yaml'
                        """,
                "common/x.yaml", "title: X\n");

        Map<String, Object> suite = loader(files).loadSuite("a.yaml");

        assertEquals(2, ((List<?>) suite.get("preprocessors")).size());
    }

    @Test
    void invalidYamlReportsParseFailure() {
        Map<String, String> files = Map.of("bad.yaml", "title: [未闭合");

        RyzeSuiteYamlLoader.LoadException ex = assertThrows(RyzeSuiteYamlLoader.LoadException.class,
                () -> loader(files).loadSuite("bad.yaml"));

        assertTrue(ex.getMessage().contains("非法"));
    }

    @Test
    void nonMapRootRejected() {
        Map<String, String> files = Map.of("list.yaml", "- 1\n- 2\n");

        RyzeSuiteYamlLoader.LoadException ex = assertThrows(RyzeSuiteYamlLoader.LoadException.class,
                () -> loader(files).loadSuite("list.yaml"));

        assertTrue(ex.getMessage().contains("根结构不是映射"));
    }

    @Test
    void pathEscapeRejected() {
        RyzeSuiteYamlLoader loader = loader(Map.of());

        RyzeSuiteYamlLoader.LoadException ex = assertThrows(RyzeSuiteYamlLoader.LoadException.class,
                () -> loader.loadSuite("../../etc/passwd"));

        assertTrue(ex.getMessage().contains("非法引用路径"));
    }
}
