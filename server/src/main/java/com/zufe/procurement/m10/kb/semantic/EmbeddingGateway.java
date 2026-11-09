package com.zufe.procurement.m10.kb.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** One bounded HTTP call, no retries. Never log response bodies or credentials. */
@Component
public class EmbeddingGateway {
    private final EmbeddingProperties properties;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build();
    public EmbeddingGateway(EmbeddingProperties properties, ObjectMapper json) {
        this.properties = properties; this.json = json;
    }
    public List<double[]> embed(List<String> texts, boolean query) {
        return embedWithUsage(texts, query).vectors();
    }
    public record EmbeddingResult(List<double[]> vectors, Long totalTokens) {}
    public EmbeddingResult embedWithUsage(List<String> texts, boolean query) {
        if (texts.isEmpty() || texts.size() > 10) throw new IllegalArgumentException("embedding_batch_1_to_10");
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) throw new IllegalStateException("embedding_no_key");
        try {
            String url = properties.getBaseUrl().replaceAll("/+$", "") + "/embeddings";
            String payload = json.writeValueAsString(Map.of("model", properties.getModel(),
                    "dimensions", properties.getDimensions(), "encoding_format", "float",
                    "input", texts.stream().map(SemanticText::normalize).toList()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(query ? properties.getQueryTimeoutMs() : properties.getBuildTimeoutMs()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("embedding_http_" + response.statusCode());
            JsonNode root = json.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) throw new IllegalStateException("embedding_count_mismatch");
            double[][] vectors = new double[texts.size()][];
            for (JsonNode item : data) {
                JsonNode indexNode = item.path("index");
                if (!indexNode.isIntegralNumber() || !indexNode.canConvertToInt()) throw new IllegalStateException("embedding_invalid_index");
                int index = indexNode.intValue();
                if (index < 0 || index >= vectors.length || vectors[index] != null) throw new IllegalStateException("embedding_invalid_index");
                JsonNode values = item.path("embedding");
                if (!values.isArray() || values.size() != properties.getDimensions()) throw new IllegalStateException("embedding_dimension_mismatch");
                double[] vector = new double[values.size()];
                for (int j = 0; j < vector.length; j++) {
                    if (!values.get(j).isNumber()) throw new IllegalStateException("embedding_invalid_value");
                    vector[j] = values.get(j).doubleValue();
                }
                SemanticText.cosine(vector, vector);
                vectors[index] = vector;
            }
            JsonNode usage = root.path("usage").path("total_tokens");
            Long tokens = usage.isIntegralNumber() && usage.canConvertToLong() && usage.longValue() >= 0
                    ? usage.longValue() : null;
            return new EmbeddingResult(Arrays.asList(vectors), tokens);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding_interrupted");
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("embedding_timeout");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("embedding_response_or_network_error");
        }
    }
}
