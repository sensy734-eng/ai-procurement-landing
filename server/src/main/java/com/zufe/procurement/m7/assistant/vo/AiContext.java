package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 多轮上下文（M7-1-3）：上一轮意图、实体与参数。
 *
 * 只用于指代消解（「它的供应商是谁」），不直接当作可信查询条件；
 * 继承来的参数与问句提取参数一样要过白名单校验（M9-2-4）。
 *
 * 除了结构化字段，还保留最近两轮问答**原文**。原因是结构化字段丢掉了语言本身：
 * 「跟上一家比呢」「第几条说的」里的指代对象只存在于上一轮的问句与答案中，
 * 把意图编码和实体名喂给模型，它仍然不知道「上一家」是谁、「第几条」在问哪件事。
 * 原文只作参考材料，其中的任何指令都不得执行——这一条写进了三处提示词。
 */
@Data
public class AiContext {

    private String intentCode;
    private String accessFingerprint;
    private List<ChatVO.EntityRef> entities;
    private Map<String, Object> params;
    /** 上一轮结果的单据号，供「这张单」类指代 */
    private String lastBillNo;
    /** 最近两轮问答原文，时间正序，最多两条 */
    private List<Turn> history;

    /** 一轮问答的原文快照。答案已截断，只保留指代需要的部分 */
    @Data
    public static class Turn {
        private String question;
        private String answer;
        /** 本轮意图编码（kb_ 开头表示知识类）。指代改写只借用知识类问句，避免拿数据查询当检索底稿 */
        private String intentCode;

        public Turn() {
        }

        public Turn(String question, String answer) {
            this(question, answer, null);
        }

        public Turn(String question, String answer, String intentCode) {
            this.question = question;
            this.answer = answer;
            this.intentCode = intentCode;
        }
    }
}
