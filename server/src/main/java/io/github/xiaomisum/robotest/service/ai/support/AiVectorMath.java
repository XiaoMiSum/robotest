package io.github.xiaomisum.robotest.service.ai.support;

/**
 * 向量数学基础设施（聚类 / 向量搜索共用）：文本反序列化、L2 归一化、点积、就地累加。
 * parseVector 与 {@link AiVectorSearchService#vectorToText(float[])} 为互逆的序列化对。
 */
public final class AiVectorMath {

    private AiVectorMath() {
    }

    /** 向量文本 → float[]（vectorToText 的反向解析，格式为 "[v1,v2,…]"）；null/空/非法格式返回 null */
    public static float[] parseVector(String text) {
        if (text == null) {
            return null;
        }
        String inner = text.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        if (inner.isBlank()) {
            return null;
        }
        String[] parts = inner.split(",");
        float[] vector = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return vector;
    }

    /** L2 归一化；零向量（范数平方 ≤ 1e-12）返回 null 避免除零，调用方按缺失处理 */
    public static float[] normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += (double) v * v;
        }
        if (sum <= 1e-12) {
            return null;
        }
        double inv = 1.0 / Math.sqrt(sum);
        float[] out = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = (float) (vector[i] * inv);
        }
        return out;
    }

    /** 点积（调用方保证同维） */
    public static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    /** 就地累加：sum[i] += v[i]（调用方保证同维） */
    public static void addInPlace(float[] sum, float[] v) {
        for (int i = 0; i < sum.length; i++) {
            sum[i] += v[i];
        }
    }
}
