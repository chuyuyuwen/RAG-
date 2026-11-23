package com.example.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 向量工具类的单元测试。这类纯函数最适合单测：不需要 Spring，跑得飞快。
 */
class VectorUtilsTest {

    @Test
    @DisplayName("余弦相似度：完全相同的向量得分为 1")
    void cosineOfSameVectorIsOne() {
        float[] v = {1f, 2f, 3f};
        assertThat(VectorUtils.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("余弦相似度：正交向量得分为 0")
    void cosineOfOrthogonalVectorsIsZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertThat(VectorUtils.cosine(a, b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("余弦相似度：方向相反得分为 -1")
    void cosineOfOppositeVectorsIsMinusOne() {
        float[] a = {1f, 2f, 3f};
        float[] b = {-1f, -2f, -3f};
        assertThat(VectorUtils.cosine(a, b)).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("余弦相似度：零向量返回 0，而不是除零异常")
    void cosineWithZeroVectorIsZero() {
        assertThat(VectorUtils.cosine(new float[] {0f, 0f}, new float[] {1f, 1f})).isZero();
    }

    @Test
    @DisplayName("维度不一致时抛出带提示的异常")
    void cosineWithMismatchedDimensionThrows() {
        assertThatThrownBy(() -> VectorUtils.cosine(new float[] {1f}, new float[] {1f, 2f}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("维度不一致");
    }

    @Test
    @DisplayName("L2 归一化后长度为 1")
    void l2NormalizeMakesUnitLength() {
        float[] v = VectorUtils.l2Normalize(new float[] {3f, 4f});
        assertThat(v[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(v[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-6f));
    }

    @Test
    @DisplayName("归一化后再算余弦，等价于点积")
    void normalizedCosineEqualsDotProduct() {
        float[] a = VectorUtils.l2Normalize(new float[] {1f, 2f, 3f});
        float[] b = VectorUtils.l2Normalize(new float[] {4f, 5f, 6f});
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        assertThat(VectorUtils.cosine(a, b)).isCloseTo(dot, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("向量序列化成字符串后能无损还原")
    void serializeAndDeserializeRoundTrip() {
        float[] original = {0.1f, -0.25f, 1.5f};
        float[] restored = VectorUtils.fromString(VectorUtils.toString(original));
        assertThat(restored).containsExactly(original);
    }

    @Test
    @DisplayName("空字符串反序列化得到空数组，不抛异常")
    void deserializeBlankGivesEmptyArray() {
        assertThat(VectorUtils.fromString("")).isEmpty();
        assertThat(VectorUtils.fromString(null)).isEmpty();
    }
}
