package com.zufe.procurement.m8.qa.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerFidelityGuardTest {

    @Test
    void rejectsDifferentNumericValuesAndUnits() {
        assertThat(AnswerFidelityGuard.check("期限为3天", "期限为30天")).isNotEmpty();
        assertThat(AnswerFidelityGuard.check("期限为30个自然日", "期限为30个工作日")).isNotEmpty();
        assertThat(AnswerFidelityGuard.check("税率为30元", "税率为30%")).isNotEmpty();
    }

    @Test
    void acceptsEquivalentAmountsPercentagesAndChineseNumerals() {
        assertThat(AnswerFidelityGuard.check("金额为30000元", "金额为3万元")).isEmpty();
        assertThat(AnswerFidelityGuard.check("比例为百分之三十", "比例为30%")).isEmpty();
        assertThat(AnswerFidelityGuard.check("期限为三十个工作日", "期限为30个工作日")).isEmpty();
        assertThat(AnswerFidelityGuard.check("金额为35,000元", "金额为三点五万元")).isEmpty();
        assertThat(AnswerFidelityGuard.check("数量为30000件", "数量为3万件")).isEmpty();
        assertThat(AnswerFidelityGuard.check("金额为1000000000000元", "金额为一万亿元")).isEmpty();
    }

    @Test
    void comparesCompleteDecimalsAndIgnoresStepOrdinals() {
        assertThat(AnswerFidelityGuard.check("金额是3.5万元", "金额是3.50万元")).isEmpty();
        assertThat(AnswerFidelityGuard.check("1. 首先确认\n2. 再处理30天", "操作期限为30天")).isEmpty();
        assertThat(AnswerFidelityGuard.check("3条", "第三条")).isNotEmpty();
        assertThat(AnswerFidelityGuard.check("期限为3天", "期限为30天")).isNotEmpty();
    }
}
