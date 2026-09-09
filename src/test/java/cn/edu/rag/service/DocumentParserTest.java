package cn.edu.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserTest {
    @TempDir Path tempDir;

    @Test
    void parsesUtf8TextFile() throws Exception {
        Path file = tempDir.resolve("资料.txt");
        Files.writeString(file, "这是可检索的课程资料。", StandardCharsets.UTF_8);

        DocumentParser.ParsedDocument result = new DocumentParser().parse(file, "资料.txt");

        assertThat(result.text()).contains("课程资料");
        assertThat(result.pageCount()).isEqualTo(1);
    }
}
