package com.zufe.procurement.m7.assistant.port;

import com.zufe.procurement.m7.assistant.vo.KnowledgeAnswer;

/**
 * 知识与教程问答端口（M8 能力边界）。
 *
 * route：1 制度问答、2 操作教程问答。两条路共用同一套双路检索，
 * 差别在答案组织：制度类给条款原文，教程类给有序步骤（M8-2-2、M8-2-3）。
 */
public interface AiKnowledgePort {

    KnowledgeAnswer answer(String question, int route);
}
