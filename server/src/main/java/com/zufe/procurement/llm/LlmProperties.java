package com.zufe.procurement.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型接入配置（app.llm.*）。
 *
 * 默认关闭：不配 api-key 时 {@link LlmGateway#available()} 恒为 false，
 * 所有调用点自动走原来的规则式链路，行为与接入前完全一致——
 * 这样「模型是可替换件」不是口号，而是配置层面就能验证的事实。
 *
 * 密钥只放 application-local.yml（已在 .gitignore 内）或环境变量 LLM_API_KEY，
 * 不进版本库、不落日志、不出现在 /llm/health 的返回里（只返回掩码）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** 总开关，对应 LLM_ENABLED */
    private boolean enabled = false;
    /** OpenAI 兼容的 /chat/completions 前缀，如 https://api.deepseek.com/v1 */
    private String baseUrl = "https://api.deepseek.com/v1";
    /** 密钥，对应 LLM_API_KEY */
    private String apiKey = "";
    /** 模型名，对应 LLM_MODEL */
    private String model = "deepseek-chat";
    /** 采样温度：判定与转述都要求稳定，默认取低值 */
    private double temperature = 0.1;
    private int timeoutMs = 30000;
    private int maxTokens = 900;

    /** 意图识别是否交给模型（关掉则退回词表 + Dice 相似度） */
    private boolean intentEnabled = true;
    /** 答案组织是否交给模型（关掉则退回模板转述） */
    private boolean answerEnabled = true;
    /** 路由（制度/教程/数据）是否交给模型 */
    private boolean routeEnabled = true;
    /** 生成后做数字一致性校验，编造数字则回退模板 */
    private boolean numberGuard = true;

    /** 健康检查与日志用的掩码，形如 sk-a****9f */
    public String maskedKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
