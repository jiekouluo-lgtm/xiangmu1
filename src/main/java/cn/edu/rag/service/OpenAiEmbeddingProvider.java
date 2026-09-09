package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "embedding-provider", havingValue = "openai")
public class OpenAiEmbeddingProvider implements EmbeddingProvider {
    private final RestClient client;
    private final AppProperties.Ai config;

    public OpenAiEmbeddingProvider(RestClient.Builder builder, AppProperties properties) {
        this.config = properties.getAi();
        RestClient.Builder configured = builder.baseUrl(trimSlash(config.getBaseUrl()));
        if (!config.getApiKey().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey());
        }
        this.client = configured.build();
    }

    @Override
    public int dimension() { return config.getDimension(); }

    @Override
    public String name() { return "OpenAI 兼容向量：" + config.getEmbeddingModel(); }

    @Override
    @SuppressWarnings("unchecked")
    public List<float[]> embed(List<String> texts) {
        if (config.getApiKey().isBlank()) throw new IllegalStateException("尚未配置 AI_API_KEY");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getEmbeddingModel());
        body.put("input", texts);
        body.put("dimensions", config.getDimension());

        Map<String, Object> response = client.post().uri("/embeddings")
                .body(body).retrieve().body(Map.class);
        if (response == null || !(response.get("data") instanceof List<?> raw)) {
            throw new IllegalStateException("向量接口未返回有效数据");
        }
        List<Map<String, Object>> rows = raw.stream().map(v -> (Map<String, Object>) v)
                .sorted(Comparator.comparingInt(v -> ((Number) v.get("index")).intValue())).toList();
        List<float[]> vectors = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            List<Number> values = (List<Number>) row.get("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) vector[i] = values.get(i).floatValue();
            if (vector.length != dimension()) throw new IllegalStateException("向量维度与配置不一致");
            vectors.add(vector);
        }
        return vectors;
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
