package com.zufe.procurement.m7.assistant.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.llm.LlmGateway;
import com.zufe.procurement.llm.LlmProperties;
import com.zufe.procurement.llm.LlmResult;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大模型接入自检（不是业务功能，是接入期与运维期的排障入口）。
 *
 * 返回三件事：配置读到了没有、密钥能不能用、一次真实调用要多久。
 * 密钥只回掩码；探活请求是一次最小调用（输出上限 16 token），
 * 因此调这个接口能看到真实错误（AUTH / TIMEOUT / NETWORK），而不是一句「已启用」。
 *
 * 权限点用审计统计的 m9:audit:list（只授系统管理员），
 * 理由：自检会消耗模型配额，不属于普通用户的问答能力。
 */
@RestController
@RequestMapping("/api/assistant/llm")
public class LlmHealthController {

    private final LlmGateway gateway;
    private final LlmProperties properties;

    public LlmHealthController(LlmGateway gateway, LlmProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    @GetMapping("/health")
    @RequirePermission("m9:audit:list")
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.isEnabled());
        body.put("available", gateway.available());
        body.put("model", properties.getModel());
        body.put("baseUrl", properties.getBaseUrl());
        body.put("keyMasked", properties.maskedKey());
        body.put("timeoutMs", properties.getTimeoutMs());
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("route", properties.isRouteEnabled());
        features.put("intent", properties.isIntentEnabled());
        features.put("answer", properties.isAnswerEnabled());
        features.put("numberGuard", properties.isNumberGuard());
        body.put("features", features);

        if (!gateway.available()) {
            body.put("probe", Map.of("ok", false,
                    "errorKind", properties.isEnabled() ? "NO_KEY" : "DISABLED"));
            body.put("hint", "在 application-local.yml 的 app.llm 下填 api-key，或设置环境变量 LLM_API_KEY");
            return Result.ok(body);
        }
        LlmResult probe = gateway.chat("你是连通性探针。", "只回复两个字：正常", 16);
        Map<String, Object> probeBody = new LinkedHashMap<>();
        probeBody.put("ok", probe.isOk());
        probeBody.put("errorKind", probe.getErrorKind());
        probeBody.put("latencyMs", probe.getLatencyMs());
        probeBody.put("reply", probe.isOk() ? abbreviate(probe.getText()) : null);
        probeBody.put("totalTokens", probe.getTotalTokens());
        body.put("probe", probeBody);
        return Result.ok(body);
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
    }
}
