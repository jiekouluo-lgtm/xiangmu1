package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import cn.edu.rag.domain.KnowledgeDocument;
import cn.edu.rag.dto.ApiModels.DocumentView;
import cn.edu.rag.repository.KnowledgeDocumentRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final KnowledgeDocumentRepository repository;
    private final DocumentParser parser;
    private final TextChunker chunker;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final Path storageRoot;

    public DocumentService(KnowledgeDocumentRepository repository, DocumentParser parser,
                           TextChunker chunker, EmbeddingProvider embeddingProvider,
                           VectorStore vectorStore, AppProperties properties) throws IOException {
        this.repository = repository;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.storageRoot = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    public List<DocumentView> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("请选择需要上传的文档");
        if (files.size() > 20) throw new IllegalArgumentException("单次最多上传 20 个文档");
        List<DocumentView> result = new ArrayList<>();
        for (MultipartFile file : files) result.add(uploadOne(file));
        return result;
    }

    public List<DocumentView> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    public Download download(long id) {
        KnowledgeDocument document = find(id);
        try {
            Resource resource = new UrlResource(resolveStorage(document).toUri());
            if (!resource.exists()) throw new IllegalStateException("原始文件已不存在");
            return new Download(document.getOriginalName(), document.getContentType(), resource);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取原始文件", exception);
        }
    }

    public void delete(long id) {
        KnowledgeDocument document = find(id);
        if (document.getStatus() == KnowledgeDocument.Status.READY) {
            vectorStore.deleteByDocumentId(id);
        } else {
            try { vectorStore.deleteByDocumentId(id); } catch (Exception ignored) { }
        }
        try {
            Files.deleteIfExists(resolveStorage(document));
        } catch (IOException exception) {
            throw new IllegalStateException("文档向量已删除，但原始文件删除失败", exception);
        }
        repository.delete(document);
    }

    public long documentCount() { return repository.count(); }
    public long readyCount() { return repository.countByStatus(KnowledgeDocument.Status.READY); }
    public long chunkCount() {
        return repository.findAll().stream().mapToLong(KnowledgeDocument::getChunkCount).sum();
    }

    private DocumentView uploadOne(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("不能上传空文件");
        String originalName = safeOriginalName(file.getOriginalFilename());
        if (!parser.supports(originalName)) throw new IllegalArgumentException(originalName + "：仅支持 PDF、TXT 和 Markdown");

        String storageName = UUID.randomUUID() + extension(originalName);
        Path target = storageRoot.resolve(storageName).normalize();
        if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("文件名不合法");

        KnowledgeDocument document = new KnowledgeDocument();
        document.setOriginalName(originalName);
        document.setStorageName(storageName);
        document.setContentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        document.setSizeBytes(file.getSize());
        document = repository.save(document);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            DocumentParser.ParsedDocument parsed = parser.parse(target, originalName);
            List<String> chunks = chunker.split(parsed.text());
            if (chunks.isEmpty()) throw new IllegalArgumentException("文档中没有可检索的文字内容");
            List<float[]> vectors = embedInBatches(chunks, 64);
            if (vectors.size() != chunks.size()) throw new IllegalStateException("向量数量与文本片段数量不一致");

            List<VectorStore.VectorChunk> rows = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                rows.add(new VectorStore.VectorChunk(document.getId() + "-" + i,
                        document.getId(), i, originalName, chunks.get(i), vectors.get(i)));
            }
            vectorStore.addAll(rows);
            document.setPageCount(parsed.pageCount());
            document.setChunkCount(chunks.size());
            document.setStatus(KnowledgeDocument.Status.READY);
            document.setProcessedAt(LocalDateTime.now());
            document.setErrorMessage(null);
        } catch (Exception exception) {
            document.setStatus(KnowledgeDocument.Status.FAILED);
            document.setErrorMessage(shortMessage(exception));
            try { vectorStore.deleteByDocumentId(document.getId()); } catch (Exception ignored) { }
        }
        return view(repository.save(document));
    }

    private KnowledgeDocument find(long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    }

    private Path resolveStorage(KnowledgeDocument document) throws IOException {
        Path path = storageRoot.resolve(document.getStorageName()).normalize();
        if (!path.startsWith(storageRoot)) throw new IOException("文件路径不合法");
        return path;
    }

    private DocumentView view(KnowledgeDocument document) {
        return new DocumentView(document.getId(), document.getOriginalName(), document.getContentType(),
                document.getSizeBytes(), document.getPageCount(), document.getChunkCount(),
                document.getStatus().name(), document.getErrorMessage(), document.getCreatedAt());
    }

    private List<float[]> embedInBatches(List<String> chunks, int batchSize) {
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            vectors.addAll(embeddingProvider.embed(chunks.subList(start, end)));
        }
        return vectors;
    }

    private String safeOriginalName(String value) {
        String name = value == null ? "document" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 500) throw new IllegalArgumentException("文件名不合法");
        return name;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase();
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 950 ? message : message.substring(0, 950);
    }

    public record Download(String fileName, String contentType, Resource resource) {}
}
