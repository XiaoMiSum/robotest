package io.github.xiaomisum.robotest.service.apitest.scan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 块注释剥离词法状态机：覆盖伪注释起始（字符串 / 字符 / 行注释内）与未闭合注释（详细设计 6.3）。
 */
class BlockCommentStripperTest {

    @Test
    void blockCommentReplacedWithSpacesKeepingLineNumbers() {
        String source = "line1\n/* hidden\nstill hidden */line2";

        String stripped = BlockCommentStripper.strip(source);

        // 长度与换行严格保持（行列偏移不变），注释内容全部消失
        assertEquals(source.length(), stripped.length());
        assertEquals(3, stripped.lines().count());
        assertEquals("line1", stripped.lines().findFirst().orElseThrow());
        assertTrue(stripped.lines().skip(1).findFirst().orElseThrow().isBlank());
        assertTrue(stripped.contains("line2") && !stripped.contains("hidden"));
    }

    @Test
    void commentMarkerInsideStringLiteralNotStripped() {
        String source = "String s = \"/* not a comment */\";";

        assertEquals(source, BlockCommentStripper.strip(source));
    }

    @Test
    void commentMarkerInsideCharLiteralNotStripped() {
        String source = "char c = '/'; char d = '*';";

        assertEquals(source, BlockCommentStripper.strip(source));
    }

    @Test
    void lineCommentWithBlockStartNotStripped() {
        String source = "// /* not a block\nint a = 1;";

        assertEquals(source, BlockCommentStripper.strip(source));
    }

    @Test
    void escapedQuoteInStringDoesNotEndLiteral() {
        String source = "String s = \"a\\\"/* still string */\"; int b;";

        assertEquals(source, BlockCommentStripper.strip(source));
    }

    @Test
    void unterminatedBlockCommentStrippedToEnd() {
        String source = "int a;\n/* never closed";

        String stripped = BlockCommentStripper.strip(source);

        assertEquals(source.length(), stripped.length());
        assertEquals("int a;", stripped.lines().findFirst().orElseThrow());
        assertTrue(stripped.lines().skip(1).findFirst().orElseThrow().isBlank());
    }

    @Test
    void adjacentCommentsAndCodeMixed() {
        String source = "int a; /* c1 */ int b; /*c2*/";

        String stripped = BlockCommentStripper.strip(source);

        assertEquals(source.length(), stripped.length());
        assertTrue(stripped.startsWith("int a; "));
        assertTrue(stripped.contains(" int b; "));
        assertTrue(!stripped.contains("c1") && !stripped.contains("c2"));
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertEquals("", BlockCommentStripper.strip(""));
        assertEquals(null, BlockCommentStripper.strip(null));
    }

    @Test
    void realWorldCommentedMethodDisappears() {
        // 蓝本取自样例工程：整段被注释掉的测试方法，剥离后不应被解析为方法
        String source = """
                public class FooTest {
                    /*
                    @Test(description = "被注释的方法")
                    @RyzeTest("foo/bar.yaml")
                    public void testHidden(TestElement<?> el) {
                    }
                    */
                    @Test(description = "有效方法")
                    @RyzeTest("foo/valid.yaml")
                    public void testValid(TestElement<?> el) {
                    }
                }
                """;

        String stripped = BlockCommentStripper.strip(source);

        assertTrue(stripped.contains("testValid"));
        assertTrue(stripped.indexOf("testHidden") < 0);
    }
}
