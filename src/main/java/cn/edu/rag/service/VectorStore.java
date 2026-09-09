package cn.edu.rag.service;

import java.util.List;

public interface VectorStore {
    record VectorChunk(String id, long documentId, int chunkIndex, String title,
                       String content, float[] embedding) {}

    record SearchHit(String id, long documentId, int chunkIndex, String title,
                     String content, float score) {}

    void addAll(List<VectorChunk> chunks);
    List<SearchHit> search(float[] query, int limit);
    void deleteByDocumentId(long documentId);
    boolean isReady();
    String name();
}
