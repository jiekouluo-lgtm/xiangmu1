package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {
    @Test
    void splitsLongTextAndKeepsContent() {
        AppProperties properties = new AppProperties();
        properties.getRag().setChunkSize(220);
        properties.getRag().setChunkOverlap(30);
        TextChunker chunker = new TextChunker(properties);
        String text = "向量数据库用于相似性检索。".repeat(60);

        List<String> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allMatch(chunk -> !chunk.isBlank() && chunk.length() <= 220);
    }

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(new TextChunker(new AppProperties()).split("  \n\t ")).isEmpty();
    }
}
