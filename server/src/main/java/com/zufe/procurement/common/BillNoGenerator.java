package com.zufe.procurement.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m11.param.entity.SysParam;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单据编号生成器，功能点 M11-4-3 的落地点。
 * 规则存 sys_param（如 CG-SQ-{yyyyMMdd}-{seq:4}），不在代码里写死前缀，
 * 管理员改参数即生效。占位符只支持 {yyyyMMdd} 与 {seq:N}，
 * N 为流水位数，与"前缀、日期段、流水长度"三要素的参数口径一致。
 * 续号由调用方按表查询最大流水（SeqResolver），生成器不感知具体单据表。
 * 并发下可能撞唯一键，由调用方插入失败后重试，重试时重新取最大流水。
 */
@Component
public class BillNoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern SEQ_PATTERN = Pattern.compile("\\{seq:(\\d+)}");

    private final SysParamMapper paramMapper;

    public BillNoGenerator(SysParamMapper paramMapper) {
        this.paramMapper = paramMapper;
    }

    /**
     * 按表内当前最大流水生成下一个单号。
     *
     * @param ruleParamCode 参数编码，如 bill_no_rule_request
     * @param resolver      续号查询，入参为去掉 {seq:N} 后的前缀
     */
    public String next(String ruleParamCode, SeqResolver resolver) {
        String rule = readRule(ruleParamCode);
        Matcher matcher = SEQ_PATTERN.matcher(rule);
        if (!matcher.find()) {
            throw new BizException(ResultCode.INVALID_PARAMETER,
                    "编号规则缺少 {seq:N} 占位符：" + ruleParamCode);
        }
        int width = Integer.parseInt(matcher.group(1));
        String prefix = matcher.replaceFirst("").replace("{yyyyMMdd}",
                LocalDate.now().format(DATE_FORMAT));
        Integer maxSeq = resolver.maxSeq(prefix);
        long nextSeq = (maxSeq == null ? 0L : maxSeq) + 1L;
        return prefix + String.format("%0" + width + "d", nextSeq);
    }

    private String readRule(String ruleParamCode) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, ruleParamCode));
        if (param == null || param.getParamValue() == null || param.getParamValue().isBlank()) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "编号规则参数未配置：" + ruleParamCode);
        }
        return param.getParamValue().trim();
    }

    /** 续号查询，按单据表实现，返回空前缀下无流水。 */
    public interface SeqResolver {
        Integer maxSeq(String prefix);
    }
}
