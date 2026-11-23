package com.example.rag.embedding;

/**
 * 向量运算工具。RAG 里最核心的数学就这几行。
 */
public final class VectorUtils {

    private VectorUtils() {
    }

    /**
     * 余弦相似度：两个向量夹角的余弦值，范围 [-1, 1]。
     *
     * <p>为什么不用欧氏距离？因为文本向量的<b>长度</b>往往反映篇幅长短而不是语义，
     * 余弦只看方向不看长度，更适合衡量「意思像不像」。
     *
     * <p>公式：dot(a,b) / (|a| * |b|)
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "向量维度不一致：a=" + a.length + "，b=" + b.length + "。"
                            + "通常意味着换过 embedding 模型但数据库里还是旧向量，需要清库重建。");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            // 空文本会得到全零向量，此时没有方向可言，相似度定义为 0
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * L2 归一化（原地修改并返回同一数组）：把向量缩放到长度为 1。
     *
     * <p>归一化之后余弦相似度就退化成点积，检索时可以少算两次开方。
     */
    public static float[] l2Normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) {
            sum += (double) x * x;
        }
        if (sum == 0.0) {
            return v;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    /**
     * 把 float[] 序列化成逗号分隔的字符串，方便存进数据库的 CLOB 字段。
     *
     * <p>为什么不用 BLOB + Java 序列化？因为文本格式肉眼可查、跨版本稳定，
     * 出问题时打开 H2 控制台就能看到向量长什么样，学习阶段这点很有价值。
     */
    public static String toString(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.toString();
    }

    /** {@link #toString(float[])} 的逆操作。 */
    public static float[] fromString(String s) {
        if (s == null || s.isBlank()) {
            return new float[0];
        }
        String[] parts = s.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i]);
        }
        return v;
    }
}
