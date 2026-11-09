package com.zufe.procurement.m10.kb.semantic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey = "";
    private String model = "text-embedding-v4";
    private int dimensions = 1024;
    private int queryTimeoutMs = 1500;
    private int buildTimeoutMs = 30000;
    private String strategy = "bm25_hash";

    public String fingerprint() {
        return SemanticText.hash(baseUrl + "|" + model + "|" + dimensions + "|" + SemanticText.VERSION);
    }
}
