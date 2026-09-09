package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "embedding-provider", havingValue = "local", matchIfMissing = true)
public class LocalHashEmbeddingProvider implements EmbeddingProvider {
    private final int dimension;

    public LocalHashEmbeddingProvider(AppProperties properties) {
        this.dimension = Math.max(64, properties.getAi().getDimension());
    }

    @Override
    public int dimension() { return dimension; }

    @Override
    public String name() { return "本地特征向量（零密钥演示）"; }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) result.add(vectorize(text));
        return result;
    }

    private float[] vectorize(String source) {
        float[] vector = new float[dimension];
        String text = source == null ? "" : source.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        for (String word : text.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) add(vector, "w:" + word, 1.6f);
        }
        String compact = text.replace(" ", "");
        for (int i = 0; i < compact.length(); i++) {
            add(vector, "c:" + compact.charAt(i), 0.7f);
            if (i + 1 < compact.length()) add(vector, "b:" + compact.substring(i, i + 2), 1.2f);
        }
        normalize(vector);
        return vector;
    }

    private void add(float[] vector, String token, float weight) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash, dimension);
        float sign = (Integer.rotateLeft(hash, 13) & 1) == 0 ? 1f : -1f;
        vector[index] += sign * weight;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) sum += value * value;
        if (sum == 0) return;
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }
}
