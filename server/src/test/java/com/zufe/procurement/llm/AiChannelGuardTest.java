package com.zufe.procurement.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m9.query.entity.AiIntent;
import com.zufe.procurement.m9.query.service.LlmQueryPlanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双通道的「无响应」一档：熔断、预算与快失败。
 *
 * 这组用例回答一个问题——模型配置正确但对端不响应时，用户等多久？
 * 改造前是等满 30 秒 HTTP 超时再回退；改造后第一次调用最多等一个截止预算，
 * 连续失败达到阈值后零等待直接走规则通道。
 *
 * 时间断言留了余量（预算 200ms，上限 600ms），因为 CI 机器抖动不该让用例变成随机失败；
 * 「零等待」一档用 50ms 上限，这已经比任何一次真实网络往返小一个数量级。
 */
class AiChannelGuardTest {

    @Test
    @DisplayName("连续失败达到阈值后打开，冷却期内零等待，探针成功才恢复")
    void opensAfterThresholdAndRecoversByProbe() throws Exception {
        LlmProperties properties = properties();
        properties.setFailureThreshold(2);
        properties.setBreakerOpenMs(120);
        AiChannelGuard guard = new AiChannelGuard(properties);

        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).isTrue();
        guard.recordFailure(AiChannelGuard.Channel.PLAN, "TIMEOUT");
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).isTrue();

        guard.recordFailure(AiChannelGuard.Channel.PLAN, "TIMEOUT");
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN))
                .as("阈值 2，第二次失败后应立刻打开")
                .isFalse();

        Thread.sleep(150);
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).as("冷却结束放一个探针").isTrue();
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).as("探针在途时第二个请求必须被挡").isFalse();

        guard.recordFailure(AiChannelGuard.Channel.PLAN, "TIMEOUT");
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).as("探针失败立刻重新打开").isFalse();

        Thread.sleep(150);
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).isTrue();
        guard.recordSuccess(AiChannelGuard.Channel.PLAN);
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).as("成功后恢复常态").isTrue();
        assertThat(guard.snapshot().get("plan").toString()).contains("CLOSED");
    }

    @Test
    @DisplayName("鉴权失败一次就打开：密钥错了，再试一万次也不会对")
    void opensImmediatelyOnAuthFailure() {
        LlmProperties properties = properties();
        properties.setFailureThreshold(10);
        AiChannelGuard guard = new AiChannelGuard(properties);

        guard.recordFailure(AiChannelGuard.Channel.ANSWER, "AUTH");
        assertThat(guard.allow(AiChannelGuard.Channel.ANSWER)).isFalse();
    }

    @Test
    @DisplayName("每条通道独立计数：成文抖动不该把理解一起熔断")
    void channelsAreIsolated() {
        LlmProperties properties = properties();
        properties.setFailureThreshold(1);
        AiChannelGuard guard = new AiChannelGuard(properties);

        guard.recordFailure(AiChannelGuard.Channel.ANSWER, "TIMEOUT");
        assertThat(guard.allow(AiChannelGuard.Channel.ANSWER)).isFalse();
        assertThat(guard.allow(AiChannelGuard.Channel.PLAN)).isTrue();
    }

    @Test
    @DisplayName("预算只收紧不放宽，且不得超过 HTTP 超时")
    void budgetIsClamped() {
        LlmProperties properties = properties();
        properties.setTimeoutMs(30_000);
        properties.setBudgetPlanMs(2_500);
        AiChannelGuard guard = new AiChannelGuard(properties);
        assertThat(guard.budgetMs(AiChannelGuard.Channel.PLAN)).isEqualTo(2_500);

        properties.setTimeoutMs(1_000);
        assertThat(guard.budgetMs(AiChannelGuard.Channel.PLAN)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("模型不响应：首调按预算放弃，熔断后零等待返回 null 交给规则通道")
    void fallsBackWithinBudgetThenInstantly() {
        LlmProperties properties = properties();
        properties.setBudgetPlanMs(200);
        properties.setFailureThreshold(3);
        AiChannelGuard guard = new AiChannelGuard(properties);
        SlowGateway gateway = new SlowGateway(5_000, "TIMEOUT", null);
        LlmQueryPlanner planner = new LlmQueryPlanner(gateway, properties, new ObjectMapper(), guard);
        List<AiIntent> intents = List.of(intent("stock_available", "库存查询"));

        long started = System.currentTimeMillis();
        assertThat(planner.plan("螺栓还有多少库存", intents, Map.of(), List.of(), null)).isNull();
        long first = System.currentTimeMillis() - started;
        System.out.println("[measure] 首次调用耗时 " + first + " ms（模拟对端 5000ms 不响应，预算 200ms）");
        assertThat(first)
                .as("改造前要等满 HTTP 超时（这里是 5000ms 的模拟延迟），改造后按 200ms 预算放弃")
                .isLessThan(600);

        planner.plan("螺栓还有多少库存", intents, Map.of(), List.of(), null);
        planner.plan("螺栓还有多少库存", intents, Map.of(), List.of(), null);
        assertThat(guard.snapshot().get("plan").toString()).contains("OPEN");

        started = System.currentTimeMillis();
        assertThat(planner.plan("螺栓还有多少库存", intents, Map.of(), List.of(), null)).isNull();
        long blocked = System.currentTimeMillis() - started;
        System.out.println("[measure] 熔断后调用耗时 " + blocked + " ms（未发起网络调用）");
        assertThat(blocked).as("熔断后不再发起网络调用").isLessThan(50);
    }

    @Test
    @DisplayName("多轮原文进提示词：结构化字段之外的指代对象只有原文能提供")
    void plannerReceivesRawHistory() {
        LlmProperties properties = properties();
        AiChannelGuard guard = new AiChannelGuard(properties);
        String payload = "{\"intent\":\"stock_available\",\"confidence\":0.9,"
                + "\"entities\":[],\"params\":{},\"reason\":\"延续上一轮\"}";
        SlowGateway gateway = new SlowGateway(0, null, payload);
        LlmQueryPlanner planner = new LlmQueryPlanner(gateway, properties, new ObjectMapper(), guard);

        AiContext context = new AiContext();
        context.setHistory(List.of(new AiContext.Turn("内六角螺栓还有多少库存", "1 号仓 120 个")));

        assertThat(planner.plan("那 2 号仓呢", List.of(intent("stock_available", "库存查询")),
                Map.of(), List.of(), context)).isNotNull();
        assertThat(gateway.lastUserPrompt)
                .contains("最近一轮问答原文")
                .contains("内六角螺栓还有多少库存")
                .contains("那 2 号仓呢");
    }

    private LlmProperties properties() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("sk-test");
        properties.setBaseUrl("http://127.0.0.1:9/v1");
        properties.setIntentEnabled(true);
        properties.setTimeoutMs(30_000);
        return properties;
    }

    private AiIntent intent(String code, String name) {
        AiIntent intent = new AiIntent();
        intent.setIntentCode(code);
        intent.setIntentName(name);
        return intent;
    }

    /** 模拟「对端活着但不响应」：睡满给定预算后返回超时，行为与 HTTP 超时一致 */
    private static class SlowGateway implements LlmGateway {

        private final long costMs;
        private final String errorKind;
        private final String text;
        private String lastUserPrompt;

        private SlowGateway(long costMs, String errorKind, String text) {
            this.costMs = costMs;
            this.errorKind = errorKind;
            this.text = text;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public LlmResult chat(String systemPrompt, String userPrompt, int maxTokens) {
            return chat(systemPrompt, userPrompt, maxTokens, 30_000);
        }

        @Override
        public LlmResult chat(String systemPrompt, String userPrompt, int maxTokens, int timeoutMs) {
            lastUserPrompt = userPrompt;
            long wait = Math.min(costMs, timeoutMs > 0 ? timeoutMs : costMs);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (errorKind != null) {
                return LlmResult.fail(errorKind, (int) wait, "fake");
            }
            return LlmResult.ok(text, (int) wait, "fake", userPrompt.length(), 0);
        }
    }
}
