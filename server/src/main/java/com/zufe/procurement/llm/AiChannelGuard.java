package com.zufe.procurement.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 通道闸门：熔断、截止预算与通道遥测。
 *
 * 它存在的理由只有一个——**让「AI 无响应」成为一条可用的通道，而不是一次等待**。
 * 在此之前，模型配置正确但对端不响应时，每个请求都要等在 30 秒 HTTP 超时之后
 * 才退回规则链路；用户感受到的不是「降级」，是「卡死」。三处调用点各等一次，
 * 一次问答最坏能拖到一分钟。
 *
 * 三层保护：
 * 1. **截止预算**：每条通道有自己的毫秒预算（路由 1.5s、理解 2.5s、成文 2.5s），
 *    到点即放弃本次模型结果，用规则通道的结果。预算按业务截止时间倒推，不是按平均延迟设。
 * 2. **熔断**：连续失败达到阈值就打开，冷却期内直接不调用模型（零等待）。
 *    计失败的不仅有超时、429、5xx，还有「HTTP 200 但输出不是 JSON / 契约不成立」——
 *    这类失败传统熔断器看不见（每一次都算成功），却同样让每个请求白烧一个预算。
 * 3. **半开探针**：冷却结束后只放一个请求过去，成功才恢复；失败立刻重新打开，
 *    避免恢复瞬间把积压流量全部砸向还没好的上游。
 *
 * 边界：闸门只决定「这次要不要调用模型」，不参与业务判断。
 * 它说「不调用」时，调用点必须像模型未配置一样走规则链路，产出与接入前逐条一致。
 */
@Slf4j
@Component
public class AiChannelGuard {

    /** 三条通道分开计数：成文模型抖动不该把理解模型一起熔断 */
    public enum Channel {
        ROUTE("route"),
        PLAN("plan"),
        ANSWER("answer");

        private final String code;

        Channel(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private static final long PROBE_TIMEOUT_MS = 10_000;

    private final LlmProperties properties;
    private final Map<Channel, State> states = new EnumMap<>(Channel.class);

    public AiChannelGuard(LlmProperties properties) {
        this.properties = properties;
        for (Channel channel : Channel.values()) {
            states.put(channel, new State());
        }
    }

    /**
     * 申请一次调用许可。
     *
     * @return false 表示熔断打开或已有探针在途，调用方必须立刻走规则通道（零等待）
     */
    public boolean allow(Channel channel) {
        State state = states.get(channel);
        synchronized (state) {
            state.calls++;
            if (state.openedAt == 0) {
                return true;
            }
            if (System.currentTimeMillis() - state.openedAt < properties.getBreakerOpenMs()) {
                state.blocked++;
                return false;
            }
            if (state.probing) {
                // 探针调用方若因为异常路径没回报结果，探针会一直挂着，通道就永远开不回来。
                // 超过一个宽松上限就视为探针丢失，重新放行一个。
                if (System.currentTimeMillis() - state.probeAt < PROBE_TIMEOUT_MS) {
                    state.blocked++;
                    return false;
                }
                state.probing = false;
            }
            state.probing = true;
            state.probeAt = System.currentTimeMillis();
            return true;
        }
    }

    public void recordSuccess(Channel channel) {
        State state = states.get(channel);
        synchronized (state) {
            if (state.openedAt != 0) {
                log.info("模型通道 {} 探针成功，熔断关闭", channel.code());
            }
            state.failures = 0;
            state.openedAt = 0;
            state.probing = false;
        }
    }

    /**
     * @param errorKind TIMEOUT / NETWORK / SERVER / RATE_LIMIT / AUTH / EMPTY / BAD_JSON / CONTRACT
     */
    public void recordFailure(Channel channel, String errorKind) {
        State state = states.get(channel);
        synchronized (state) {
            state.failures++;
            state.lastError = errorKind;
            state.lastFailureAt = System.currentTimeMillis();
            state.probing = false;
            boolean auth = "AUTH".equals(errorKind);
            if (auth || state.failures >= Math.max(1, properties.getFailureThreshold())) {
                if (state.openedAt == 0) {
                    log.warn("模型通道 {} 熔断打开（{}，连续失败 {} 次），{} ms 内不再调用",
                            channel.code(), errorKind, state.failures, properties.getBreakerOpenMs());
                }
                state.openedAt = System.currentTimeMillis();
            }
        }
    }

    /** 本次调用的截止预算：配置值不得超过 HTTP 超时，否则「预算」名存实亡 */
    public int budgetMs(Channel channel) {
        int configured = switch (channel) {
            case ROUTE -> properties.getBudgetRouteMs();
            case PLAN -> properties.getBudgetPlanMs();
            case ANSWER -> properties.getBudgetAnswerMs();
        };
        int ceiling = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : configured;
        return Math.max(200, Math.min(configured, ceiling));
    }

    /** 运维视图：健康检查接口直接返回这份快照，不用翻日志猜通道状态 */
    public Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Channel channel : Channel.values()) {
            State state = states.get(channel);
            Map<String, Object> one = new LinkedHashMap<>();
            synchronized (state) {
                one.put("state", state.openedAt == 0 ? "CLOSED" : state.probing ? "HALF_OPEN" : "OPEN");
                one.put("failures", state.failures);
                one.put("calls", state.calls);
                one.put("blocked", state.blocked);
                one.put("lastError", state.lastError);
                long remaining = state.openedAt == 0 ? 0
                        : Math.max(0, properties.getBreakerOpenMs() - (System.currentTimeMillis() - state.openedAt));
                one.put("openRemainingMs", remaining);
                one.put("budgetMs", budgetMs(channel));
            }
            body.put(channel.code(), one);
        }
        return body;
    }

    private static class State {
        private int failures;
        private long openedAt;
        private boolean probing;
        private long probeAt;
        private String lastError;
        private long lastFailureAt;
        private int calls;
        private int blocked;
    }
}
