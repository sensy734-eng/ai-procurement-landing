package com.zufe.procurement.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的模型客户端（/chat/completions）。
 *
 * 用 JDK 自带 HttpClient 而不是引 SDK：与项目「导入导出不引 POI、图表不引 ECharts」
 * 的取向一致，同时也避免 SDK 版本与 Spring Boot 管理版本打架。
 * 只要厂商兼容这套协议（DeepSeek、通义、Kimi、vLLM 等），改 base-url 与 model 即可切换。
 *
 * 三条实现约定：
 * 1. 任何异常都转成 {@link LlmResult#getErrorKind()}，不向业务抛；
 * 2. 密钥不出现在日志与异常信息里；
 * 3. 超时按配置，默认 30 秒——评测里 P1 的延迟预算就是按这个口径量的。
 */
@Slf4j
@Component
public class OpenAiCompatibleGateway implements LlmGateway {

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public OpenAiCompatibleGateway(LlmProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(properties.getTimeoutMs(), 10000)))
                .build();
    }

    @Override
    public boolean available() {
        return properties.isEnabled()
                && properties.getApiKey() != null && !properties.getApiKey().isBlank()
                && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    @Override
    public LlmResult chat(String systemPrompt, String userPrompt, int maxTokens) {
        return chat(systemPrompt, userPrompt, maxTokens, properties.getTimeoutMs());
    }

    @Override
    public LlmResult chat(String systemPrompt, String userPrompt, int maxTokens, int timeoutMs) {
        if (!properties.isEnabled()) {
            return LlmResult.fail("DISABLED", 0, properties.getModel());
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return LlmResult.fail("NO_KEY", 0, properties.getModel());
        }
        // 预算只能收紧不能放宽：配置里写了个更大的值也不允许超过 HTTP 超时
        int budget = timeoutMs > 0 ? Math.min(timeoutMs, properties.getTimeoutMs()) : properties.getTimeoutMs();
        long started = System.currentTimeMillis();
        String body;
        try {
            body = objectMapper.writeValueAsString(payload(systemPrompt, userPrompt, maxTokens));
        } catch (Exception e) {
            return LlmResult.fail("BAD_REQUEST", elapsed(started), properties.getModel());
        }
        int promptChars = length(systemPrompt) + length(userPrompt);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .timeout(Duration.ofMillis(budget))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int latency = elapsed(started);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("模型鉴权失败：HTTP {}，请检查 app.llm.api-key", response.statusCode());
                return LlmResult.fail("AUTH", latency, properties.getModel());
            }
            if (response.statusCode() == 429) {
                log.warn("模型限流：HTTP 429");
                return LlmResult.fail("RATE_LIMIT", latency, properties.getModel());
            }
            if (response.statusCode() >= 400) {
                log.warn("模型返回异常状态：HTTP {}，{}", response.statusCode(), abbreviate(response.body()));
                return LlmResult.fail("SERVER", latency, properties.getModel());
            }
            return parse(response.body(), latency, promptChars);
        } catch (HttpTimeoutException e) {
            log.warn("模型调用超出本次预算（{} ms）", budget);
            return LlmResult.fail("TIMEOUT", elapsed(started), properties.getModel());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LlmResult.fail("NETWORK", elapsed(started), properties.getModel());
        } catch (Exception e) {
            log.warn("模型调用失败：{}", e.getClass().getSimpleName() + " " + abbreviate(e.getMessage()));
            return LlmResult.fail("NETWORK", elapsed(started), properties.getModel());
        }
    }

    private Map<String, Object> payload(String systemPrompt, String userPrompt, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", maxTokens > 0 ? maxTokens : properties.getMaxTokens());
        body.put("stream", false);
        // 思维链开关：只有配置了才发这个字段——多数厂商不认它，乱发会直接 400。
        // DeepSeek-V4 系列默认开思维链，短分类任务会把 max_tokens 全烧在推理上、正文为空。
        if (properties.getThinkingType() != null && !properties.getThinkingType().isBlank()) {
            body.put("thinking", Map.of("type", properties.getThinkingType().trim()));
        }
        return body;
    }

    private LlmResult parse(String raw, int latency, int promptChars) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("模型响应缺少 choices：{}", abbreviate(raw));
                return LlmResult.fail("EMPTY", latency, properties.getModel());
            }
            String text = choices.get(0).path("message").path("content").asText("");
            // 部分推理模型把正文放在 reasoning_content 之外的空 content 里，兜底取一次
            if (text.isBlank()) {
                text = choices.get(0).path("message").path("reasoning_content").asText("");
            }
            Integer tokens = root.path("usage").path("total_tokens").isNumber()
                    ? root.path("usage").path("total_tokens").asInt() : null;
            if (text.isBlank()) {
                return LlmResult.fail("EMPTY", latency, properties.getModel());
            }
            return LlmResult.ok(text, latency, root.path("model").asText(properties.getModel()),
                    promptChars, tokens);
        } catch (Exception e) {
            log.warn("模型响应不是合法 JSON：{}", abbreviate(raw));
            return LlmResult.fail("BAD_JSON", latency, properties.getModel());
        }
    }

    private String endpoint() {
        String base = properties.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private static int elapsed(long started) {
        return (int) (System.currentTimeMillis() - started);
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }
}
