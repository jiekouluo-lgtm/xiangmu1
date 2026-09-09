package cn.edu.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String storagePath = "./uploads";
    private final VectorStore vectorStore = new VectorStore();
    private final Milvus milvus = new Milvus();
    private final Ai ai = new Ai();
    private final Rag rag = new Rag();

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public VectorStore getVectorStore() { return vectorStore; }
    public Milvus getMilvus() { return milvus; }
    public Ai getAi() { return ai; }
    public Rag getRag() { return rag; }

    public static class VectorStore {
        private String type = "milvus";
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class Milvus {
        private String uri = "http://localhost:19530";
        private String token = "root:Milvus";
        private String collection = "literature_chunks";
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
    }

    public static class Ai {
        private String embeddingProvider = "local";
        private String chatProvider = "local";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String embeddingModel = "text-embedding-3-small";
        private String chatModel = "gpt-4.1-mini";
        private int dimension = 384;
        public String getEmbeddingProvider() { return embeddingProvider; }
        public void setEmbeddingProvider(String embeddingProvider) { this.embeddingProvider = embeddingProvider; }
        public String getChatProvider() { return chatProvider; }
        public void setChatProvider(String chatProvider) { this.chatProvider = chatProvider; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getEmbeddingModel() { return embeddingModel; }
        public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
        public String getChatModel() { return chatModel; }
        public void setChatModel(String chatModel) { this.chatModel = chatModel; }
        public int getDimension() { return dimension; }
        public void setDimension(int dimension) { this.dimension = dimension; }
    }

    public static class Rag {
        private int chunkSize = 700;
        private int chunkOverlap = 120;
        private int topK = 5;
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        public int getChunkOverlap() { return chunkOverlap; }
        public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }
}
