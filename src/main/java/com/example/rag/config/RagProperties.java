package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 相关的全部配置项，对应 application.properties 里 {@code rag.*} 开头的键。
 *
 * <p>Spring Boot 会把配置文件里的 {@code rag.embedding.base-url} 自动绑定到
 * {@link Embedding#getBaseUrl()}，靠的是「宽松绑定」规则：中划线、下划线、大小写都等价。
 *
 * <p>这里用普通 class + getter/setter 而不是 record，因为 Spring 对嵌套可变对象的绑定最稳定，
 * 而且以后加字段不用改构造器。
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private final Embedding embedding = new Embedding();
    private final Chunk chunk = new Chunk();
    private final Retrieval retrieval = new Retrieval();
    private final Llm llm = new Llm();

    public Embedding getEmbedding() {
        return embedding;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Llm getLlm() {
        return llm;
    }

    /** 向量化（embedding）相关配置。 */
    public static class Embedding {

        /** fake = 本地哈希假向量（不联网）；real = 调用在线 API。 */
        private String provider = "fake";

        /** OpenAI 兼容的接口地址，例如 https://api.siliconflow.cn/v1 。 */
        private String baseUrl = "https://api.siliconflow.cn/v1";

        /** 模型名，例如 BAAI/bge-m3。 */
        private String model = "BAAI/bge-m3";

        /** API Key。留空则 real 模式会启动时报错。 */
        private String apiKey = "";

        /** 向量维度。bge-m3 是 1024，假实现也按这个维度生成。 */
        private int dimension = 1024;

        /** 单次请求最多送多少条文本，避免超出服务端限制。 */
        private int batchSize = 32;

        /** 网络超时（秒）。 */
        private int timeoutSeconds = 30;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** 文本切块配置。 */
    public static class Chunk {

        /** 每块最多多少字符。 */
        private int size = 500;

        /** 相邻块重叠多少字符，避免句子被切断后语义丢失。 */
        private int overlap = 100;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    /** 检索配置。 */
    public static class Retrieval {

        /** 取相似度最高的前 K 个片段。 */
        private int topK = 5;

        /** 相似度低于此值的片段直接丢弃，避免把无关内容喂给模型。 */
        private double minScore = 0.2;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }
    }

    /** 生成模型（DeepSeek 等）配置。 */
    public static class Llm {

        private String baseUrl = "https://api.deepseek.com/v1";
        private String model = "deepseek-chat";
        private String apiKey = "";
        private double temperature = 0.2;
        private int maxTokens = 1500;
        private int timeoutSeconds = 60;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
