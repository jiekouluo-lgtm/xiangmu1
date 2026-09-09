package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "chat-provider", havingValue = "openai")
public class OpenAiChatProvider implements ChatProvider {
    private final RestClient client;
    private final AppProperties.Ai config;

    public OpenAiChatProvider(RestClient.Builder builder, AppProperties properties) {
        this.config = properties.getAi();
        RestClient.Builder configured = builder.baseUrl(trimSlash(config.getBaseUrl()));
        if (!config.getApiKey().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey());
        }
        this.client = configured.build();
    }

    @Override
    public String name() { return "OpenAI 兼容对话：" + config.getChatModel(); }

    @Override
    @SuppressWarnings("unchecked")
    public String answer(String question, List<VectorStore.SearchHit> sources) {
        if (config.getApiKey().isBlank()) throw new IllegalStateException("尚未配置 AI_API_KEY");
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            VectorStore.SearchHit hit = sources.get(i);
            context.append("[").append(i + 1).append("] 文档：").append(hit.title())
                    .append("，片段：").append(hit.chunkIndex() + 1).append("\n")
                    .append(hit.content()).append("\n\n");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是严谨的文献知识库助手。只根据给定资料回答；资料不足时明确说明。引用事实时使用[1]、[2]格式标注来源，不编造出处。用简洁中文作答。"));
        messages.add(Map.of("role", "user", "content",
                "资料：\n" + context + "\n问题：" + question));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getChatModel());
        body.put("temperature", 0.2);
        body.put("messages", messages);
        Map<String, Object> response = client.post().uri("/chat/completions")
                .body(body).retrieve().body(Map.class);
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("对话接口未返回有效数据");
        }
        Map<String, Object> choice = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        return String.valueOf(message.get("content"));
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
