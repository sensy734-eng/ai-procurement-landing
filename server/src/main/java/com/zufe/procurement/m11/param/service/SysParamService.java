package com.zufe.procurement.m11.param.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.param.entity.SysParam;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import com.zufe.procurement.m11.param.vo.ParamVO;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 系统参数服务。覆盖功能点：
 * M11-4-1 价格偏离阈值配置、M11-4-2 库存预警下限配置、M11-4-3 单据编号规则配置。
 * 参数口径集中在此校验，其他模块只读取不解释，
 * 保证“口径固定于配置、不随查询变化”这一条能被代码结构证明。
 */
@Service
public class SysParamService {

    /** 单据编号模板必须含日期段与流水段 */
    private static final Pattern BILL_NO_DATE = Pattern.compile("\\{yyyyMMdd}");
    private static final Pattern BILL_NO_SEQ = Pattern.compile("\\{seq:(\\d)}");

    private final SysParamMapper paramMapper;
    private final OperationLogService operationLogService;

    public SysParamService(SysParamMapper paramMapper, OperationLogService operationLogService) {
        this.paramMapper = paramMapper;
        this.operationLogService = operationLogService;
    }

    /**
     * 按分组查询参数。分组为空时返回全部，便于后台参数页一次加载。
     */
    public List<ParamVO> list(String group) {
        List<SysParam> rows = paramMapper.selectList(new LambdaQueryWrapper<SysParam>()
                .eq(group != null && !group.isBlank(), SysParam::getParamGroup, group)
                .orderByAsc(SysParam::getParamGroup)
                .orderByAsc(SysParam::getId));
        return rows.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 更新参数值。不可编辑参数直接拒绝；类型与业务范围逐项校验。
     */
    @Transactional
    public void update(String paramCode, String paramValue) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, paramCode));
        if (param == null) {
            throw new BizException(ResultCode.NOT_FOUND, "参数不存在：" + paramCode);
        }
        if (param.getEditable() != null && param.getEditable() == 0) {
            throw new BizException(ResultCode.NO_PERMISSION, "该参数不允许界面修改：" + param.getParamName());
        }
        String normalized = paramValue == null ? "" : paramValue.trim();
        validate(param, normalized);

        String before = param.getParamValue();
        paramMapper.update(null, new LambdaUpdateWrapper<SysParam>()
                .eq(SysParam::getId, param.getId())
                .set(SysParam::getParamValue, normalized));
        operationLogService.record("M11", "config", "sys_param", param.getId(), paramCode,
                "参数变更 " + param.getParamName() + "：" + before + " -> " + normalized);
    }

    /** 供其他模块读取，缺值时返回默认值 */
    public int getInt(String paramCode, int defaultValue) {
        String raw = rawValue(paramCode);
        try {
            return raw == null ? defaultValue : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String paramCode, BigDecimal defaultValue) {
        String raw = rawValue(paramCode);
        try {
            return raw == null ? defaultValue : new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getString(String paramCode, String defaultValue) {
        String raw = rawValue(paramCode);
        return raw == null || raw.isBlank() ? defaultValue : raw;
    }

    private String rawValue(String paramCode) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, paramCode));
        return param == null ? null : param.getParamValue();
    }

    /**
     * 参数校验规则按编码集中在此，新增参数时必须同步补规则，否则默认只做类型校验。
     */
    private void validate(SysParam param, String value) {
        if (value.isEmpty()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "参数值不能为空");
        }
        switch (param.getParamType()) {
            case "int" -> {
                int number = parseInt(value, param);
                validateIntRange(param.getParamCode(), number);
            }
            case "decimal" -> {
                BigDecimal number = parseDecimal(value, param);
                validateDecimalRange(param.getParamCode(), number);
            }
            case "boolean" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "布尔参数只能为 true 或 false");
                }
            }
            case "json" -> {
                if (!value.startsWith("{") && !value.startsWith("[")) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "JSON 参数需以 { 或 [ 开头");
                }
            }
            default -> {
                // string 类型只做长度约束，业务规则在下方按编码补充
            }
        }
        if (param.getParamCode().startsWith("bill_no_rule")) {
            validateBillNoRule(value);
        }
        if ("deviation_threshold_focus".equals(param.getParamCode())
                || "deviation_threshold_significant".equals(param.getParamCode())) {
            validateDeviationOrder(param.getParamCode(), value);
        }
    }

    private int parseInt(String value, SysParam param) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    param.getParamName() + " 必须为整数");
        }
    }

    private BigDecimal parseDecimal(String value, SysParam param) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    param.getParamName() + " 必须为数值");
        }
    }

    /** 整数参数的合理区间，防止把演示环境配成不可用状态 */
    private void validateIntRange(String code, int value) {
        switch (code) {
            case "session_timeout_minutes" -> requireBetween(code, value, 5, 1440, "会话超时分钟数");
            case "pwd_min_length" -> requireBetween(code, value, 6, 30, "密码最小长度");
            case "price_stat_window_value" -> requireBetween(code, value, 1, 100, "统计窗口取值");
            case "stock_alert_default" -> {
                if (value < 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "库存预警下限不能为负数");
                }
            }
            case "kb_chunk_size" -> requireBetween(code, value, 100, 2000, "切块字数");
            case "kb_chunk_overlap" -> requireBetween(code, value, 0, 500, "切块重叠字数");
            case "kb_recall_topk" -> requireBetween(code, value, 1, 20, "召回条数");
            case "ai_history_round" -> requireBetween(code, value, 1, 50, "上下文保留轮数");
            default -> {
                // 未列出的整数参数只要求是整数
            }
        }
    }

    private void requireBetween(String code, int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    label + "需在 " + min + " 至 " + max + " 之间，当前 " + value + "（" + code + "）");
        }
    }

    /** 小数参数限定在 0 至 1 之间，用于阈值与相似度 */
    private void validateDecimalRange(String code, BigDecimal value) {
        switch (code) {
            case "deviation_threshold_focus", "deviation_threshold_significant",
                 "kb_score_threshold", "ai_intent_confidence_threshold" -> {
                if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "阈值需在 0 至 1 之间");
                }
            }
            case "stock_alert_default" -> {
                if (value.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BizException(ResultCode.INVALID_PARAMETER, "库存预警下限不能为负数");
                }
            }
            default -> {
                // 其他小数参数不限范围
            }
        }
    }

    /**
     * 偏离阈值两个参数必须保持“关注阈值小于显著阈值”，
     * 否则 M6 的偏离判定会出现显著档永远无法命中。
     */
    private void validateDeviationOrder(String code, String value) {
        BigDecimal focus = getDecimal("deviation_threshold_focus", new BigDecimal("0.05"));
        BigDecimal significant = getDecimal("deviation_threshold_significant", new BigDecimal("0.15"));
        BigDecimal target = new BigDecimal(value);
        if ("deviation_threshold_focus".equals(code)) {
            focus = target;
        } else {
            significant = target;
        }
        if (focus.compareTo(significant) >= 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "偏离关注阈值必须小于偏离显著阈值，当前 " + focus + " 与 " + significant);
        }
    }

    /**
     * 单据编号模板校验：必须含 {yyyyMMdd} 与 {seq:n}，流水位数 2 至 6。
     */
    private void validateBillNoRule(String value) {
        if (!BILL_NO_DATE.matcher(value).find()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "编号规则必须包含日期段 {yyyyMMdd}");
        }
        Matcher matcher = BILL_NO_SEQ.matcher(value);
        if (!matcher.find()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "编号规则必须包含流水段 {seq:n}");
        }
        int digits = Integer.parseInt(matcher.group(1));
        if (digits < 2 || digits > 6) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "流水位数需为 2 至 6 位");
        }
    }

    private ParamVO toVO(SysParam entity) {
        ParamVO vo = new ParamVO();
        vo.setId(entity.getId());
        vo.setParamCode(entity.getParamCode());
        vo.setParamName(entity.getParamName());
        vo.setParamValue(entity.getParamValue());
        vo.setParamType(entity.getParamType());
        vo.setParamGroup(entity.getParamGroup());
        vo.setEditable(entity.getEditable());
        vo.setRemark(entity.getRemark());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
