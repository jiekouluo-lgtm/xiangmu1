package cn.edu.rag.service;

import java.util.List;

public interface EmbeddingProvider {
    int dimension();
    String name();
    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }
}
