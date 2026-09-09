package cn.edu.rag.service;

import cn.edu.rag.config.AppProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.vector-store", name = "type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    private static final String ID = "chunk_id";
    private static final String DOCUMENT_ID = "document_id";
    private static final String CHUNK_INDEX = "chunk_index";
    private static final String TITLE = "title";
    private static final String CONTENT = "content";
    private static final String EMBEDDING = "embedding";

    private final AppProperties properties;
    private final EmbeddingProvider embeddingProvider;
    private final Gson gson = new Gson();
    private volatile MilvusClientV2 client;
    private volatile boolean initialized;

    public MilvusVectorStore(AppProperties properties, EmbeddingProvider embeddingProvider) {
        this.properties = properties;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public void addAll(List<VectorChunk> chunks) {
        if (chunks.isEmpty()) return;
        ensureCollection();
        int batchSize = 200;
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            List<JsonObject> rows = new ArrayList<>(end - start);
            for (VectorChunk chunk : chunks.subList(start, end)) {
                JsonObject row = new JsonObject();
                row.addProperty(ID, chunk.id());
                row.addProperty(DOCUMENT_ID, chunk.documentId());
                row.addProperty(CHUNK_INDEX, chunk.chunkIndex());
                row.addProperty(TITLE, truncate(chunk.title(), 500));
                row.addProperty(CONTENT, truncate(chunk.content(), 65000));
                row.add(EMBEDDING, gson.toJsonTree(chunk.embedding()));
                rows.add(row);
            }
            client().insert(InsertReq.builder()
                    .collectionName(collection()).data(rows).build());
        }
    }

    @Override
    public List<SearchHit> search(float[] query, int limit) {
        ensureCollection();
        SearchResp response = client().search(SearchReq.builder()
                .collectionName(collection())
                .annsField(EMBEDDING)
                .data(Collections.singletonList(new FloatVec(query)))
                .topK(limit)
                .outputFields(List.of(DOCUMENT_ID, CHUNK_INDEX, TITLE, CONTENT))
                .build());
        if (response.getSearchResults().isEmpty()) return List.of();
        List<SearchHit> hits = new ArrayList<>();
        for (SearchResp.SearchResult result : response.getSearchResults().get(0)) {
            Map<String, Object> entity = result.getEntity();
            hits.add(new SearchHit(String.valueOf(result.getId()),
                    number(entity.get(DOCUMENT_ID)).longValue(),
                    number(entity.get(CHUNK_INDEX)).intValue(),
                    String.valueOf(entity.get(TITLE)), String.valueOf(entity.get(CONTENT)),
                    result.getScore()));
        }
        return hits;
    }

    @Override
    public void deleteByDocumentId(long documentId) {
        ensureCollection();
        client().delete(DeleteReq.builder().collectionName(collection())
                .filter(DOCUMENT_ID + " == " + documentId).build());
    }

    @Override
    public boolean isReady() {
        try {
            ensureCollection();
            return true;
        } catch (RuntimeException exception) {
            log.debug("Milvus 尚未就绪：{}", exception.getMessage());
            return false;
        }
    }

    @Override
    public String name() { return "Milvus · " + collection(); }

    private synchronized void ensureCollection() {
        if (initialized) return;
        boolean exists = client().hasCollection(HasCollectionReq.builder()
                .collectionName(collection()).build());
        if (!exists) createCollection();
        initialized = true;
    }

    private void createCollection() {
        CreateCollectionReq.CollectionSchema schema = client().createSchema();
        schema.addField(AddFieldReq.builder().fieldName(ID).dataType(DataType.VarChar)
                .maxLength(128).isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName(DOCUMENT_ID).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName(CHUNK_INDEX).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName(TITLE).dataType(DataType.VarChar).maxLength(512).build());
        schema.addField(AddFieldReq.builder().fieldName(CONTENT).dataType(DataType.VarChar).maxLength(65535).build());
        schema.addField(AddFieldReq.builder().fieldName(EMBEDDING).dataType(DataType.FloatVector)
                .dimension(embeddingProvider.dimension()).build());

        IndexParam idIndex = IndexParam.builder().fieldName(ID)
                .indexType(IndexParam.IndexType.AUTOINDEX).build();
        IndexParam vectorIndex = IndexParam.builder().fieldName(EMBEDDING)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE).build();
        client().createCollection(CreateCollectionReq.builder()
                .collectionName(collection()).collectionSchema(schema)
                .indexParams(List.of(idIndex, vectorIndex)).build());
        log.info("已创建 Milvus 集合 {}，向量维度 {}", collection(), embeddingProvider.dimension());
    }

    private MilvusClientV2 client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    AppProperties.Milvus config = properties.getMilvus();
                    client = new MilvusClientV2(ConnectConfig.builder()
                            .uri(config.getUri()).token(config.getToken()).build());
                }
            }
        }
        return client;
    }

    private String collection() { return properties.getMilvus().getCollection(); }
    private Number number(Object value) {
        if (value instanceof Number number) return number;
        return Long.parseLong(String.valueOf(value));
    }
    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
