package com.zufe.procurement.m7;

import com.zufe.procurement.m7.assistant.support.RouteLexicon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只读边界：写操作判定要看语气，不能只看词。
 *
 * 起因是一条真实误拒——「查一下这张订单审批通过没有」被判成写操作。
 * 写操作词表里「审批通过」「入库确认」既是动作也是状态名，contains 命中必然误伤，
 * 所以把边界改成「命中写词 + 这句是在下指令」两个条件同时成立。
 *
 * 这组用例锁两件事：
 *   ① 该拒的写指令一条都不能漏（作废、改库存、删物料、审批通过）；
 *   ② 该放的状态问句一条都不能拒（通过没有、通过了吗、哪些作废了、帮我查…）。
 */
class RouteLexiconWriteBoundaryTest {

    @Test
    @DisplayName("明确的写指令必须拦住")
    void refusesWriteInstructions() {
        assertThat(RouteLexicon.isWriteInstruction("帮我把订单 CG-DD-20261011-0001 作废")).isTrue();
        assertThat(RouteLexicon.isWriteInstruction("把内六角螺栓的库存改成 100")).isTrue();
        assertThat(RouteLexicon.isWriteInstruction("删除物料 M-1001")).isTrue();
        assertThat(RouteLexicon.isWriteInstruction("帮我审批通过这张订单")).isTrue();
        assertThat(RouteLexicon.isWriteInstruction("把 ai_query_audit 表清空")).isTrue();
        // 带疑问语气的请求仍然是请求：问「可不可以做」不等于「在做」
        assertThat(RouteLexicon.isWriteInstruction("把库存改成 100 可以吗")).isTrue();
    }

    @Test
    @DisplayName("问单子状态的问句不能被当成写指令")
    void allowsStateQuestions() {
        assertThat(RouteLexicon.isWriteInstruction("查一下这张订单审批通过没有")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("这张订单审批通过了吗")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("帮我查一下这张订单审批通过没有")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("订单 CG-DD-20261011-0001 审批通过了没")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("哪些订单已经作废了")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("这张入库单确认了没有")).isFalse();
    }

    @Test
    @DisplayName("不含写操作词的问句天然不触发边界")
    void ignoresQuestionsWithoutWriteWord() {
        assertThat(RouteLexicon.isWriteInstruction("内六角螺栓还有多少库存")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("怎么提交采购申请")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction(null)).isFalse();
    }

    @Test
    @DisplayName("命令标记后面接查询动词时不算命令")
    void commandMarkerFollowedByQueryVerbIsNotCommand() {
        // 「帮我」后面是「查」，是让我查不是让我改
        assertThat(RouteLexicon.isWriteInstruction("帮我看看这张单作废没有")).isFalse();
        assertThat(RouteLexicon.isWriteInstruction("请查一下订单作废情况")).isFalse();
        // 「帮我」后面直接是写动作，才算命令
        assertThat(RouteLexicon.isWriteInstruction("帮我作废这张单")).isTrue();
    }
}
