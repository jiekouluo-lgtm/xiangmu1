package cn.edu.rag.web;

import cn.edu.rag.dto.ApiModels.*;
import cn.edu.rag.service.DocumentService;
import cn.edu.rag.service.RagService;
import cn.edu.rag.service.VectorStore;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DocumentService documents;
    private final RagService rag;
    private final VectorStore vectorStore;

    public ApiController(DocumentService documents, RagService rag, VectorStore vectorStore) {
        this.documents = documents;
        this.rag = rag;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        boolean ready = vectorStore.isReady();
        return new SystemStatus(ready, vectorStore.name(), rag.embeddingName(), rag.chatName(),
                ready ? "系统运行正常" : "Milvus 尚未连接，请检查 Docker 服务");
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return new Dashboard(documents.documentCount(), documents.readyCount(), documents.chunkCount(),
                rag.count(), rag.history(5));
    }

    @GetMapping("/documents")
    public List<DocumentView> listDocuments() { return documents.list(); }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DocumentView> upload(@RequestPart("files") List<MultipartFile> files) {
        return documents.upload(files);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        DocumentService.Download download = documents.download(id);
        MediaType type;
        try {
            type = download.contentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(download.contentType());
        } catch (IllegalArgumentException ignored) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }

    @DeleteMapping("/documents/{id}")
    public Message delete(@PathVariable long id) {
        documents.delete(id);
        return new Message("文档已删除");
    }

    @PostMapping("/chat")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return rag.ask(request.question());
    }

    @GetMapping("/history")
    public List<HistoryView> history(@RequestParam(defaultValue = "50") int limit) {
        return rag.history(limit);
    }

    @DeleteMapping("/history")
    public Message clearHistory() {
        rag.clearHistory();
        return new Message("问答记录已清空");
    }
}
