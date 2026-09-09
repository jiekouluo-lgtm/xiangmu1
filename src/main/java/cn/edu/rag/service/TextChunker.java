package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    private static final char[] BREAKS = {'\n', '。', '！', '？', '.', '!', '?', '；', ';'};
    private final int chunkSize;
    private final int overlap;

    public TextChunker(AppProperties properties) {
        this.chunkSize = Math.max(200, properties.getRag().getChunkSize());
        this.overlap = Math.min(Math.max(0, properties.getRag().getChunkOverlap()), chunkSize / 2);
    }

    public List<String> split(String rawText) {
        String text = normalize(rawText);
        if (text.isBlank()) return List.of();

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(text.length(), start + chunkSize);
            int end = hardEnd;
            if (hardEnd < text.length()) {
                int minimumBreak = start + chunkSize / 2;
                int best = -1;
                for (char candidate : BREAKS) {
                    int found = text.lastIndexOf(candidate, hardEnd - 1);
                    if (found >= minimumBreak) best = Math.max(best, found + 1);
                }
                if (best > start) end = best;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= text.length()) break;
            int next = Math.max(start + 1, end - overlap);
            start = skipWhitespace(text, next);
        }
        return chunks;
    }

    private int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        return index;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
