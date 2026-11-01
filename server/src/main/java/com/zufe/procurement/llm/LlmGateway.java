package com.zufe.procurement.llm;

/**
 * 大模型调用端口。
 *
 * 全系统只有这一处与外部模型通信，业务模块一律依赖本接口而不是 HTTP 客户端：
 * 换厂商只改 base-url 与 model（OpenAI 兼容协议），换本地模型只需再写一个实现，
 * m8/m9 的代码不动。这一点与 M7 用 Port 隔离 M8/M9 是同一个思路。
 */
public interface LlmGateway {

    /** 是否可用：开关打开、密钥已配、地址非空 */
    boolean available();

    /**
     * 单轮对话：系统提示 + 用户提示。
     *
     * @param maxTokens 本次期望的最大输出长度，按场景给（判定类给小值，生成类给大值）
     */
    LlmResult chat(String systemPrompt, String userPrompt, int maxTokens);
}
