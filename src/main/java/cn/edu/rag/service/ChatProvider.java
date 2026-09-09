package cn.edu.rag.service;

import java.util.List;

public interface ChatProvider {
    String name();
    String answer(String question, List<VectorStore.SearchHit> sources);
}
