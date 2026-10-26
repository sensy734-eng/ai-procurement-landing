package com.zufe.procurement.m10.kb.support;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * 本地确定性向量器（M10-3-1 向量化调用）。
 *
 * 规划书 3.3 的模型接入策略是厂商无关：向量化走统一接口，接口不可用时降级。
 * 本实现为降级路径上的确定性向量器——字符二元组哈希到固定维度的词袋向量，
 * 同样输入必得同样输出，不依赖任何外部服务，使双路检索对照实验可复现。
 * 后续接入真实嵌入模型时新增一个 Vectorizer 实现即可，索引内 vector_model
 * 标识向量来源，重建索引时按当前实现全量刷新。
 */
@Service
public class HashingVectorizer {

    /** 向量维度：512 维，float32 序列化后每块 2KB，kb_chunk.embedding 可容纳 */
    public static final int DIM = 512;

    public static final String MODEL = "hash-bigram-v1";

    /** 文本 → L2 归一化后的 float32 字节 */
    public byte[] vectorize(String text) {
        float[] vector = new float[DIM];
        Map<Integer, Integer> hits = new HashMap<>();
        for (String token : TextTokens.tokenize(text)) {
            int slot = Math.floorMod(token.hashCode(), DIM);
            hits.merge(slot, 1, Integer::sum);
        }
        double norm = 0;
        for (Map.Entry<Integer, Integer> entry : hits.entrySet()) {
            float value = entry.getValue();
            vector[entry.getKey()] = value;
            norm += (double) value * value;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIM; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        ByteBuffer buffer = ByteBuffer.allocate(DIM * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    /** 余弦相似度：两侧均已 L2 归一化，点积即余弦 */
    public static double cosine(byte[] left, byte[] right) {
        if (left == null || right == null || left.length != right.length || left.length % 4 != 0) {
            return 0;
        }
        double dot = 0;
        int count = left.length / 4;
        for (int i = 0; i < count; i++) {
            float a = ByteBuffer.wrap(left, i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            float b = ByteBuffer.wrap(right, i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            dot += (double) a * b;
        }
        return dot;
    }

    /** 供测试与调试：token 列表直接向量化 */
    public byte[] vectorize(List<String> tokens) {
        return vectorize(String.join(" ", tokens));
    }
}
