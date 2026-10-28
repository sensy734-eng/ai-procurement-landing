package com.zufe.procurement.m9.query.service;

import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m9.query.entity.AiIntentParam;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数白名单校验（M9-2-4、M9-2-5）。
 *
 * 校验四件事，任一不过即拒：
 * 1. 参数编码在 ai_intent_param 内（不在就是模型幻觉或用户提了不支持的条件）；
 * 2. 值能按 data_type 解析；
 * 3. 操作符在 operator_set 内；
 * 4. 值在 enum_values 内（该列为空表示不做枚举限制）。
 *
 * 必填缺失不填默认值，返回澄清（M9-2-5）——默认值会让用户以为系统「猜对了」。
 */
@Service
public class ParamValidator {

    public Validation validate(List<AiIntentParam> specs, List<RawParam> raws) {
        Validation validation = new Validation();
        for (RawParam raw : raws) {
            AiIntentParam spec = specs.stream()
                    .filter(item -> item.getParamCode().equals(raw.getCode()))
                    .findFirst().orElse(null);
            if (spec == null) {
                validation.reject(raw, "该参数不在白名单内");
                continue;
            }
            Object value = parse(spec.getDataType(), raw.getValue());
            if (value == null) {
                validation.reject(raw, "参数类型不符合 " + spec.getDataType());
                continue;
            }
            String operator = operatorOf(spec.getParamCode());
            if (spec.getOperatorSet() != null && !operatorAllowed(spec.getOperatorSet(), operator)) {
                validation.reject(raw, "操作符 " + operator + " 不在允许范围（" + spec.getOperatorSet() + "）");
                continue;
            }
            if (spec.getEnumValues() != null && !spec.getEnumValues().isBlank()
                    && !enumAllowed(spec.getEnumValues(), value)) {
                validation.reject(raw, "取值不在允许范围（" + spec.getEnumValues() + "）");
                continue;
            }
            validation.accept(spec, raw, value);
        }
        for (AiIntentParam spec : specs) {
            if (spec.getRequired() != null && spec.getRequired() == 1
                    && !validation.getAccepted().containsKey(spec.getParamCode())) {
                ChatVO.ParamItem item = new ChatVO.ParamItem();
                item.setCode(spec.getParamCode());
                item.setName(spec.getParamName());
                item.setDataType(spec.getDataType());
                item.setOp(operatorOf(spec.getParamCode()));
                item.setEditable(Boolean.TRUE);
                item.setEnumValues(splitEnum(spec.getEnumValues()));
                validation.getMissing().add(item);
            }
        }
        return validation;
    }

    /** 参数编码后缀决定操作符，与 ai_intent_param.operator_set 对齐 */
    public static String operatorOf(String code) {
        if (code == null) {
            return "eq";
        }
        if (code.endsWith("_gte") || code.endsWith("_from")) {
            // date_from 是区间左端点，语义就是「不早于」，按 eq 校验会误拒
            return "gte";
        }
        if (code.endsWith("_lte") || code.endsWith("_to")) {
            return "lte";
        }
        if ("order_no".equals(code)) {
            return "like";
        }
        return "eq";
    }

    private boolean operatorAllowed(String operatorSet, String operator) {
        return Arrays.stream(operatorSet.split(",")).map(String::trim)
                .anyMatch(item -> item.equals(operator) || ("like".equals(operator) && item.equals("eq")));
    }

    private boolean enumAllowed(String enumValues, Object value) {
        String text = String.valueOf(value);
        return Arrays.stream(enumValues.split(",")).map(String::trim).anyMatch(text::equals);
    }

    private static List<String> splitEnum(String enumValues) {
        if (enumValues == null || enumValues.isBlank()) {
            return null;
        }
        return Arrays.stream(enumValues.split(",")).map(String::trim).toList();
    }

    private Object parse(String dataType, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return switch (dataType == null ? "string" : dataType) {
                case "long" -> Long.valueOf(new BigDecimal(text).longValue());
                case "int" -> Integer.valueOf(Integer.parseInt(text));
                case "decimal" -> new BigDecimal(text);
                case "date" -> LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
                case "boolean" -> Boolean.valueOf(text);
                default -> text;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 参数原始值：来源决定优先级（override > question > context） */
    @Data
    public static class RawParam {
        private String code;
        private Object value;
        private String source;
        private String label;

        public RawParam(String code, Object value, String source, String label) {
            this.code = code;
            this.value = value;
            this.source = source;
            this.label = label;
        }
    }

    @Data
    public static class Validation {
        private Map<String, Object> accepted = new LinkedHashMap<>();
        private List<ChatVO.ParamItem> filters = new ArrayList<>();
        private List<ChatVO.ParamItem> rejected = new ArrayList<>();
        private List<ChatVO.ParamItem> missing = new ArrayList<>();
        /** 被拒参数的原始值与原因，写进审计（rejected_params_json） */
        private List<Map<String, Object>> rejectedSnapshot = new ArrayList<>();

        void accept(AiIntentParam spec, RawParam raw, Object value) {
            accepted.put(spec.getParamCode(), value);
            ChatVO.ParamItem item = new ChatVO.ParamItem();
            item.setCode(spec.getParamCode());
            item.setName(spec.getParamName());
            item.setOp(operatorOf(spec.getParamCode()));
            item.setValue(value);
            item.setDataType(spec.getDataType());
            item.setEnumValues(splitEnum(spec.getEnumValues()));
            item.setEditable(Boolean.TRUE);
            item.setSource(raw.getSource());
            filters.add(item);
        }

        void reject(RawParam raw, String reason) {
            ChatVO.ParamItem item = new ChatVO.ParamItem();
            item.setCode(raw.getCode());
            item.setName(raw.getCode());
            item.setValue(raw.getValue());
            item.setSource(raw.getSource());
            item.setReason(reason);
            rejected.add(item);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("code", raw.getCode());
            snapshot.put("value", raw.getValue());
            snapshot.put("reason", reason);
            snapshot.put("source", raw.getSource());
            rejectedSnapshot.add(snapshot);
        }
    }
}
