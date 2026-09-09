package cn.edu.rag.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class DocumentParser {
    public record ParsedDocument(String text, int pageCount) {}

    public ParsedDocument parse(Path path, String originalName) throws IOException {
        String extension = extensionOf(originalName);
        return switch (extension) {
            case "pdf" -> parsePdf(path);
            case "txt", "md", "markdown" -> new ParsedDocument(
                    Files.readString(path, StandardCharsets.UTF_8), 1);
            default -> throw new IllegalArgumentException("仅支持 PDF、TXT 和 Markdown 文档");
        };
    }

    public boolean supports(String fileName) {
        return switch (extensionOf(fileName)) {
            case "pdf", "txt", "md", "markdown" -> true;
            default -> false;
        };
    }

    private ParsedDocument parsePdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(document);
            return new ParsedDocument(text, document.getNumberOfPages());
        }
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
