package com.zufe.procurement.m7.assistant.port;

import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m7.assistant.vo.KnowledgeAnswer;

/**
 * 知识与教程问答端口（M8 能力边界）。
 *
 * route：1 制度问答、2 操作教程问答。两条路共用同一套双路检索，
 * 差别在答案组织：制度类给条款原文，教程类给有序步骤（M8-2-2、M8-2-3）。
 */
public interface AiKnowledgePort {

    KnowledgeAnswer answer(String question, int route);

    /**
     * 带多轮上下文的问答。缺省实现忽略上下文，保持旧调用方不受影响。
     *
     * 需要上下文的原因是「第几条说的」这类追问：它没有业务名词，
     * 直接拿去检索等于用一句空话搜全库；补全成「不合格品能入库吗 + 第几条说的」
     * 才检索得到上一轮那条条款。
     */
    default KnowledgeAnswer answer(String question, int route, AiContext context) {
        return answer(question, route);
    }
}
