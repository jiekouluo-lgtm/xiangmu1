package cn.edu.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public record AskRequest(
            @NotBlank(message = "请输入问题")
            @Size(max = 1000, message = "问题不能超过 1000 个字符")
            String question) {}

    public record SourceSnippet(long documentId, String title, int chunkIndex,
                                String content, float score) {}

    public record AskResponse(String answer, List<SourceSnippet> sources, long durationMs) {}

    public record DocumentView(long id, String name, String contentType, long sizeBytes,
                               int pageCount, int chunkCount, String status,
                               String errorMessage, LocalDateTime createdAt) {}

    public record HistoryView(long id, String question, String answer, int sourceCount,
                              long durationMs, LocalDateTime createdAt) {}

    public record Dashboard(long documentCount, long readyCount, long chunkCount,
                            long questionCount, List<HistoryView> recentQuestions) {}

    public record SystemStatus(boolean ready, String vectorStore, String embeddingProvider,
                               String chatProvider, String message) {}

    public record Message(String message) {}
}
