package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import cn.edu.rag.domain.QueryHistory;
import cn.edu.rag.dto.ApiModels.AskResponse;
import cn.edu.rag.dto.ApiModels.HistoryView;
import cn.edu.rag.dto.ApiModels.SourceSnippet;
import cn.edu.rag.repository.QueryHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {
    private final EmbeddingProvider embeddingProvider;
    private final ChatProvider chatProvider;
    private final VectorStore vectorStore;
    private final QueryHistoryRepository historyRepository;
    private final int topK;

    public RagService(EmbeddingProvider embeddingProvider, ChatProvider chatProvider,
                      VectorStore vectorStore, QueryHistoryRepository historyRepository,
                      AppProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.chatProvider = chatProvider;
        this.vectorStore = vectorStore;
        this.historyRepository = historyRepository;
        this.topK = Math.max(1, Math.min(10, properties.getRag().getTopK()));
    }

    public AskResponse ask(String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isBlank()) throw new IllegalArgumentException("请输入问题");
        long started = System.nanoTime();
        List<VectorStore.SearchHit> hits = vectorStore.search(embeddingProvider.embed(question), topK);
        String answer = chatProvider.answer(question, hits);
        long durationMs = (System.nanoTime() - started) / 1_000_000;

        QueryHistory history = new QueryHistory();
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSourceCount(hits.size());
        history.setDurationMs(durationMs);
        historyRepository.save(history);

        List<SourceSnippet> sources = hits.stream().map(hit -> new SourceSnippet(
                hit.documentId(), hit.title(), hit.chunkIndex(), hit.content(), hit.score())).toList();
        return new AskResponse(answer, sources, durationMs);
    }

    public List<HistoryView> history(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        return historyRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream().map(item -> new HistoryView(item.getId(), item.getQuestion(), item.getAnswer(),
                        item.getSourceCount(), item.getDurationMs(), item.getCreatedAt())).toList();
    }

    public long count() { return historyRepository.count(); }
    public void clearHistory() { historyRepository.deleteAllInBatch(); }
    public String embeddingName() { return embeddingProvider.name(); }
    public String chatName() { return chatProvider.name(); }
}
