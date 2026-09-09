package cn.edu.rag.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(prefix = "app.vector-store", name = "type", havingValue = "memory")
public class InMemoryVectorStore implements VectorStore {
    private final CopyOnWriteArrayList<VectorChunk> chunks = new CopyOnWriteArrayList<>();

    @Override
    public void addAll(List<VectorChunk> values) {
        values.forEach(value -> chunks.add(new VectorChunk(value.id(), value.documentId(),
                value.chunkIndex(), value.title(), value.content(), value.embedding().clone())));
    }

    @Override
    public List<SearchHit> search(float[] query, int limit) {
        return chunks.stream()
                .map(chunk -> new SearchHit(chunk.id(), chunk.documentId(), chunk.chunkIndex(),
                        chunk.title(), chunk.content(), cosine(query, chunk.embedding())))
                .sorted(Comparator.comparing(SearchHit::score).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void deleteByDocumentId(long documentId) {
        chunks.removeIf(chunk -> chunk.documentId() == documentId);
    }

    @Override
    public boolean isReady() { return true; }

    @Override
    public String name() { return "内存向量库（开发模式）"; }

    private float cosine(float[] left, float[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0, l2 = 0, r2 = 0;
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            l2 += left[i] * left[i];
            r2 += right[i] * right[i];
        }
        return l2 == 0 || r2 == 0 ? 0 : (float) (dot / Math.sqrt(l2 * r2));
    }
}
