package io.github.xiaomisum.robotest.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiVectorMath 纯函数单测：向量文本互逆解析、L2 归一化（零向量 → null）、
 * 点积与就地累加（聚类 / 向量搜索共用）。
 */
class AiVectorMathTest {

    @Test
    void parseVector_bracketed() {
        float[] v = AiVectorMath.parseVector("[1.5,2.0,3.0]");
        assertNotNull(v);
        assertArrayEquals(new float[]{1.5f, 2.0f, 3.0f}, v);
    }

    @Test
    void parseVector_unbracketed() {
        float[] v = AiVectorMath.parseVector("1.5,2.0");
        assertNotNull(v);
        assertArrayEquals(new float[]{1.5f, 2.0f}, v);
    }

    @Test
    void parseVector_nullOrBlankReturnsNull() {
        assertNull(AiVectorMath.parseVector(null));
        assertNull(AiVectorMath.parseVector(""));
        assertNull(AiVectorMath.parseVector("[]"));
        assertNull(AiVectorMath.parseVector("[  ]"));
    }

    @Test
    void parseVector_malformedReturnsNull() {
        assertNull(AiVectorMath.parseVector("[1.0,abc]"));
        assertNull(AiVectorMath.parseVector("[1.0,"));
    }

    @Test
    void normalize_unitLength() {
        float[] v = AiVectorMath.normalize(new float[]{3.0f, 4.0f});
        assertNotNull(v);
        assertEquals(3.0 / 5.0, v[0], 1e-6);
        assertEquals(4.0 / 5.0, v[1], 1e-6);
    }

    @Test
    void normalize_zeroVectorReturnsNull() {
        assertNull(AiVectorMath.normalize(new float[]{0.0f, 0.0f}));
    }

    @Test
    void dot_sumsProducts() {
        assertEquals(32.0, AiVectorMath.dot(new float[]{1.0f, 2.0f, 3.0f}, new float[]{4.0f, 5.0f, 6.0f}), 1e-9);
    }

    @Test
    void addInPlace_accumulates() {
        float[] sum = new float[]{1.0f, 2.0f};
        AiVectorMath.addInPlace(sum, new float[]{3.0f, 4.0f});
        assertArrayEquals(new float[]{4.0f, 6.0f}, sum);
    }

    @Test
    void normalizedDot_isCosineSimilarityWithinUnit() {
        float[] a = AiVectorMath.normalize(new float[]{1.0f, 2.0f, 3.0f});
        float[] b = AiVectorMath.normalize(new float[]{3.0f, 2.0f, 1.0f});
        assertNotNull(a);
        assertNotNull(b);
        double cos = AiVectorMath.dot(a, b);
        assertTrue(cos >= -1.0 && cos <= 1.0);
    }
}
