package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashEmbeddingProviderTest {
    @Test
    void createsNormalizedVectorsWithConfiguredDimension() {
        AppProperties properties = new AppProperties();
        properties.getAi().setDimension(128);
        LocalHashEmbeddingProvider provider = new LocalHashEmbeddingProvider(properties);

        float[] first = provider.embed("Milvus 向量数据库支持语义检索");
        float[] similar = provider.embed("使用 Milvus 向量数据库进行检索");
        float[] unrelated = provider.embed("今天的天气非常晴朗");

        assertThat(first).hasSize(128);
        assertThat(norm(first)).isCloseTo(1.0, within(0.0001));
        assertThat(cosine(first, similar)).isGreaterThan(cosine(first, unrelated));
    }

    private double norm(float[] value) {
        double sum = 0;
        for (float item : value) sum += item * item;
        return Math.sqrt(sum);
    }

    private double cosine(float[] left, float[] right) {
        double value = 0;
        for (int i = 0; i < left.length; i++) value += left[i] * right[i];
        return value;
    }

    private org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
