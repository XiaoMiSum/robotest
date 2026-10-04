package io.github.xiaomisum.robotest.service.ai.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorChunkerTest {

    @Test
    void blankInputYieldsNoChunks() {
        assertTrue(VectorChunker.chunk(null).isEmpty());
        assertTrue(VectorChunker.chunk("   \n  ").isEmpty());
    }

    @Test
    void titleLineStandsAsOwnChunk() {
        List<String> chunks = VectorChunker.chunk("登录功能验收标准\n正文说明第一段");

        assertEquals(2, chunks.size());
        assertEquals("登录功能验收标准", chunks.get(0));
        assertEquals("正文说明第一段", chunks.get(1));
    }

    @Test
    void shortBodyLinesMergeIntoParagraph() {
        String line = "甲".repeat(100);
        List<String> chunks = VectorChunker.chunk(line + "\n" + line + "\n" + line);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).length() <= VectorChunker.MAX_CHARS);
        assertTrue(chunks.get(0).contains("\n"));
    }

    @Test
    void oversizedLineSplitsWithOverlap() {
        String line = "字".repeat(1200);

        List<String> chunks = VectorChunker.chunk(line);

        assertEquals(3, chunks.size());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= VectorChunker.MAX_CHARS));
        // 相邻窗口重叠 OVERLAP_CHARS，跨边界语义不丢
        assertEquals(line.substring(450, 500), chunks.get(1).substring(0, VectorChunker.OVERLAP_CHARS));
        assertTrue(line.endsWith(chunks.get(2)));
    }

    @Test
    void bodyLongerThanWindowClosesParagraphBeforeSplitting() {
        String body = "乙".repeat(600);
        List<String> chunks = VectorChunker.chunk("前置短行\n" + body + "\n尾行");

        assertEquals(4, chunks.size());
        assertEquals("前置短行", chunks.get(0));
        assertEquals("尾行", chunks.get(3));
    }
}
