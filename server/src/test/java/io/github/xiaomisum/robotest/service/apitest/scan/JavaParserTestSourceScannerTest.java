package io.github.xiaomisum.robotest.service.apitest.scan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaParserTestSourceScanner 测试：夹具蓝本取自样例工程 nisoahr-api-test
 * （@Test(description) + @RyzeTest(yaml) 成对标注形态），覆盖验收要点
 * 「块注释方法不计、两类失败分列」（SRS BR-1.6 / 计划 WP-3.1）。
 */
class JavaParserTestSourceScannerTest {

    private final JavaParserTestSourceScanner scanner = new JavaParserTestSourceScanner();

    private static TestSourceScanner.SourceFile file(String path, String content) {
        return new TestSourceScanner.SourceFile(path, content);
    }

    @Test
    void pairedAnnotationsScannedAsValidCases() {
        String source = """
                package com.nistone.qa.projectname.test.supervision;

                import io.github.xiaomisum.ryze.support.testng.annotation.RyzeTest;
                import org.testng.annotations.Test;

                public class SupervisionLetterTest {

                    @Test(description = "督办函流程 PC 端创建")
                    @RyzeTest("supervision/letter/supervision_letter_pc.yaml")
                    public void testSupervisionLetterPc(TestElement<?> el) {
                    }

                    @Test(description = "督办函流程 审批通过")
                    @RyzeTest("supervision/letter/supervision_letter_status_pass.yaml")
                    public void testSupervisionLetterStatusPass(TestElement<?> el) {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(
                file("src/test/java/com/nistone/qa/projectname/test/supervision/SupervisionLetterTest.java", source)));

        assertEquals(1, outcome.classes().size());
        ScannedClass scanned = outcome.classes().get(0);
        assertEquals("com.nistone.qa.projectname.test.supervision", scanned.packageName());
        assertEquals("SupervisionLetterTest", scanned.className());
        assertEquals("com.nistone.qa.projectname.test.supervision.SupervisionLetterTest", scanned.fullyQualifiedName());
        assertEquals(List.of(
                        new ScannedMethod("testSupervisionLetterPc", "督办函流程 PC 端创建",
                                "supervision/letter/supervision_letter_pc.yaml"),
                        new ScannedMethod("testSupervisionLetterStatusPass", "督办函流程 审批通过",
                                "supervision/letter/supervision_letter_status_pass.yaml")),
                scanned.methods());
        assertTrue(outcome.parseFailed().isEmpty());
        assertTrue(outcome.errorFailed().isEmpty());
    }

    @Test
    void methodInsideBlockCommentNotCounted() {
        String source = """
                public class FooTest {
                    /*
                    @Test(description = "被注释掉的方法")
                    @RyzeTest("foo/hidden.yaml")
                    public void testHidden(TestElement<?> el) {
                    }
                    */

                    @Test(description = "有效方法")
                    @RyzeTest("foo/valid.yaml")
                    public void testValid(TestElement<?> el) {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertEquals(1, outcome.classes().size());
        assertEquals(List.of(new ScannedMethod("testValid", "有效方法", "foo/valid.yaml")),
                outcome.classes().get(0).methods());
        assertTrue(outcome.parseFailed().isEmpty());
        assertTrue(outcome.errorFailed().isEmpty());
    }

    @Test
    void unpairedAnnotationsIgnored() {
        String source = """
                public class FooTest {
                    @Test(description = "只有 Test")
                    public void testOnlyTest() {
                    }

                    @RyzeTest("foo/only_ryze.yaml")
                    public void testOnlyRyze() {
                    }

                    public void testNoAnnotation() {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertTrue(outcome.classes().isEmpty());
        assertTrue(outcome.parseFailed().isEmpty());
        assertTrue(outcome.errorFailed().isEmpty());
    }

    @Test
    void emptyRyzeTestValueCountedAsParseFailed() {
        String source = """
                public class FooTest {
                    @Test(description = "模板路径为空")
                    @RyzeTest("")
                    public void testEmptyPath() {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertTrue(outcome.classes().isEmpty());
        assertEquals(1, outcome.parseFailed().size());
        assertEquals("FooTest#testEmptyPath", outcome.parseFailed().get(0).methodKey());
        assertTrue(outcome.parseFailed().get(0).reason().contains("步骤模板路径"));
        assertTrue(outcome.errorFailed().isEmpty());
    }

    @Test
    void missingDescriptionCountedAsParseFailed() {
        String source = """
                public class FooTest {
                    @Test
                    @RyzeTest("foo/valid.yaml")
                    public void testNoDescription() {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertTrue(outcome.classes().isEmpty());
        assertEquals(1, outcome.parseFailed().size());
        assertTrue(outcome.parseFailed().get(0).reason().contains("description"));
        assertTrue(outcome.errorFailed().isEmpty());
    }

    @Test
    void syntaxErrorCountedAsErrorFailed() {
        String source = """
                public class FooTest {
                    @Test(description = "语法错误文件")
                    @RyzeTest("foo/valid.yaml")
                    public void testBroken( {
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertTrue(outcome.classes().isEmpty());
        assertTrue(outcome.parseFailed().isEmpty());
        assertEquals(1, outcome.errorFailed().size());
        assertEquals(null, outcome.errorFailed().get(0).methodKey());
        assertTrue(outcome.errorFailed().get(0).reason().contains("解析失败"));
    }

    @Test
    void fullyQualifiedAnnotationMatched() {
        String source = """
                public class FooTest {
                    @org.testng.annotations.Test(description = "全限定写法")
                    @io.github.xiaomisum.ryze.support.testng.annotation.RyzeTest("foo/valid.yaml")
                    public void testFqn() {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertEquals(1, outcome.classes().size());
        assertEquals(List.of(new ScannedMethod("testFqn", "全限定写法", "foo/valid.yaml")),
                outcome.classes().get(0).methods());
    }

    @Test
    void valueMemberStyleRyzeTestMatched() {
        String source = """
                public class FooTest {
                    @Test(description = "value 显式写法")
                    @RyzeTest(value = "foo/valid.yaml")
                    public void testValueMember() {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", source)));

        assertEquals(1, outcome.classes().size());
        assertEquals("foo/valid.yaml", outcome.classes().get(0).methods().get(0).yamlPath());
    }

    @Test
    void mixedFailuresCountedSeparately() {
        String source = """
                public class FooTest {
                    @Test(description = "可预期失败")
                    @RyzeTest("")
                    public void testExpectable() {
                    }

                    @Test(description = "有效方法")
                    @RyzeTest("foo/valid.yaml")
                    public void testValid() {
                    }
                }
                """;
        String broken = "public class BrokenTest { public void (";

        ScanOutcome outcome = scanner.scan(List.of(
                file("src/test/java/FooTest.java", source),
                file("src/test/java/BrokenTest.java", broken)));

        assertEquals(1, outcome.classes().size());
        assertEquals(1, outcome.parseFailed().size());
        assertEquals(1, outcome.errorFailed().size());
    }

    @Test
    void classWithoutValidCasesNotReported() {
        String source = """
                public class HelperTest {
                    public void helper() {
                    }
                }
                """;

        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/HelperTest.java", source)));

        assertTrue(outcome.classes().isEmpty());
        assertTrue(outcome.parseFailed().isEmpty());
        assertTrue(outcome.errorFailed().isEmpty());
    }

    @Test
    void blankFileCountedAsErrorFailed() {
        ScanOutcome outcome = scanner.scan(List.of(file("src/test/java/FooTest.java", "   ")));

        assertTrue(outcome.classes().isEmpty());
        assertEquals(1, outcome.errorFailed().size());
        assertTrue(outcome.errorFailed().get(0).reason().contains("为空"));
    }
}
