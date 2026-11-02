package com.zufe.procurement.m8.qa.service;

import com.zufe.procurement.m7.assistant.vo.AiContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指代型追问的确定性改写。
 *
 * 「第几条说的」这类问句没有业务名词，直接拿去检索等于用一句空话搜全库。
 * 改写规则必须是窄的：只在「短问句 + 指代词」同时成立时借用上一轮问句，
 * 否则会把用户已经换了的话题重新拽回来。
 */
class KnowledgeFollowUpTest {

    @Test
    @DisplayName("短追问补上上一轮问句")
    void rewritesShortFollowUp() {
        AiContext context = history("不合格品能入库吗", "不合格品不得入库，让步接收除外。");
        assertThat(KnowledgeQaService.resolveQuestion("第几条说的", context))
                .isEqualTo("不合格品能入库吗 第几条说的");
        assertThat(KnowledgeQaService.resolveQuestion("那这条呢", context))
                .isEqualTo("不合格品能入库吗 那这条呢");
    }

    @Test
    @DisplayName("自带主体词的问句不改写，哪怕带「这个」")
    void keepsSelfContainedQuestion() {
        AiContext context = history("不合格品能入库吗", "不合格品不得入库。");
        String question = "这个物料的库存是多少";
        assertThat(KnowledgeQaService.resolveQuestion(question, context)).isEqualTo(question);
    }

    @Test
    @DisplayName("没有历史或没有指代词时原样返回")
    void leavesPlainQuestionUntouched() {
        assertThat(KnowledgeQaService.resolveQuestion("第几条说的", null)).isEqualTo("第几条说的");
        assertThat(KnowledgeQaService.resolveQuestion("供应商准入条件是什么", history("随便一问", "随便一答")))
                .isEqualTo("供应商准入条件是什么");
    }

    @Test
    @DisplayName("上一轮是数据查询时不借用：数据问句里没有制度术语，拿来当检索底稿只会带偏")
    void doesNotBorrowFromDataQuery() {
        AiContext context = new AiContext();
        context.setHistory(List.of(new AiContext.Turn("跟上一家比呢", "均价 12.50 元", "purchase_price_history")));
        assertThat(KnowledgeQaService.resolveQuestion("第几条说的", context)).isEqualTo("第几条说的");
    }

    private AiContext history(String question, String answer) {
        AiContext context = new AiContext();
        context.setHistory(List.of(new AiContext.Turn(question, answer, "kb_document_qa")));
        return context;
    }
}
