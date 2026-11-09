package com.zufe.procurement.m7.assistant.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.zufe.procurement.m7.assistant.task.TaskContracts.*;

class TaskParserTest {
    ObjectMapper json = new ObjectMapper();
    @ParameterizedTest @CsvSource({"二百,200", "两千零三,2003", "十,10", "一万零二十,10020", "一千二百三十四,1234", "12.3456,12.3456"})
    void preciseQuantities(String text, String value) { assertThat(DraftParser.quantity(text)).isEqualByComparingTo(value); }
    @Test void thousandsSeparator() { assertThat(DraftParser.quantity("1,200.50")).isEqualByComparingTo("1200.5"); }
    @ParameterizedTest @ValueSource(strings={"2,5","一百二","一二","两三百","0","-2","10000001","0.00001","几个","1e3","一万万","十百"})
    void ambiguousOrInvalidQuantitiesNeedConfirmation(String text) { assertThat(DraftParser.quantity(text)).isNull(); }
    @Test void calendarDatesAreDeterministicAndStrict() {
        var monday=LocalDate.of(2026,11,9);
        assertThat(DraftParser.date("下周五",monday)).isEqualTo(LocalDate.of(2026,11,20));
        assertThat(DraftParser.date("本周日",monday)).isEqualTo(LocalDate.of(2026,11,15));
        assertThat(DraftParser.date("明天",monday)).isEqualTo(monday.plusDays(1));
        assertThat(DraftParser.date("2026年2月30日",monday)).isNull();
        assertThat(DraftParser.date("月底前",monday)).isNull();
    }
    @Test void templateIsExplicitAndNeverParsesFreeTextAsModelOutput() {
        assertThat(DraftParser.template("物料：螺栓；数量：200；单位：个\n物料：电缆；数量：30；单位：米")).hasSize(2);
        assertThat(DraftParser.template("买200个螺栓")).isEmpty();
        assertThat(DraftParser.template("物料：螺栓；物料：电缆")).isEmpty();
    }
    @Test void extractedFieldsMustBeFullSourceSpans() {
        String original="需要30个螺栓";
        String valid="{\"items\":[{\"source\":\"需要30个螺栓\",\"materialText\":\"螺栓\",\"quantityText\":\"30\",\"unitText\":\"个\"}]}";
        assertThat(DraftParser.model(valid,original,json).get(0).quantity()).isEqualTo("30");
        assertThatThrownBy(() -> DraftParser.model(valid.replace("\"30\"","\"3\""),original,json)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DraftParser.model(valid.replace("\"螺栓\"","\"传感器\""),original,json)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DraftParser.model("not json",original,json)).isInstanceOf(IllegalArgumentException.class);
    }
    List<Evidence> evidence() {
        return List.of(new Evidence(1,"order_item","交期","剩余30个，交期30个工作日",null,1L,null),
                new Evidence(2,"order_item","另一条","剩余3个，交期3个自然日",null,2L,null));
    }
    @ParameterizedTest @ValueSource(strings={"[0]","[3]","[1.5]","[\"1\"]","[2147483648]","[1,1]","[]"})
    void invalidReferencesFail(String refs) {
        assertThatThrownBy(() -> OrderReviewService.validate("{\"observations\":[{\"text\":\"剩余30个\",\"evidenceIds\":"+refs+"}]}",evidence(),json)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"剩余3个","交期30个自然日","金额30元"})
    void cannotBorrowNumbersOrChangeUnits(String claim) {
        assertThatThrownBy(() -> OrderReviewService.validate("{\"observations\":[{\"text\":\""+claim+"\",\"evidenceIds\":[1]}]}",evidence(),json)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void legitimateEquivalentQuantityPasses() {
        assertThat(OrderReviewService.validate("{\"observations\":[{\"text\":\"剩余三十个\",\"evidenceIds\":[1]}]}",evidence(),json)).hasSize(1);
    }
}
