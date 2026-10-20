package com.zufe.procurement.m11.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.CsvWriter;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m11.audit.entity.AiQueryAudit;
import com.zufe.procurement.m11.audit.mapper.AiQueryAuditMapper;
import com.zufe.procurement.m11.audit.vo.AiQueryAuditVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能查询审计服务，功能点 M11-4-5 审计列表、M11-4-6 审计详情与导出。
 * 审计由 M9 在执行时写入，本服务只读，保证受控执行边界的可查证据不被后台改写。
 */
@Service
public class AiQueryAuditService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiQueryAuditMapper auditMapper;

    public AiQueryAuditService(AiQueryAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    /**
     * M11-4-5 审计列表。按用户、意图、结果码与时间范围过滤。
     */
    public PageResult<AiQueryAuditVO> page(long pageNo, long pageSize, String username,
                                           String intentCode, String resultCode,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        Page<AiQueryAudit> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100));
        Page<AiQueryAudit> result = auditMapper.selectPage(page,
                buildWrapper(username, intentCode, resultCode, startTime, endTime).orderByDesc(AiQueryAudit::getId));
        List<AiQueryAuditVO> records = result.getRecords().stream().map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * M11-4-6 审计详情，含被拒参数与注入的数据范围。
     */
    public AiQueryAuditVO detail(Long id) {
        AiQueryAudit entity = id == null ? null : auditMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "审计记录不存在");
        }
        return toVO(entity);
    }

    /**
     * M11-4-6 审计导出，CSV 格式，含原始问句与解析参数，便于论文举证。
     */
    public byte[] exportCsv(String username, String intentCode, String resultCode,
                            LocalDateTime startTime, LocalDateTime endTime) {
        List<AiQueryAudit> rows = auditMapper.selectList(
                buildWrapper(username, intentCode, resultCode, startTime, endTime)
                        .orderByDesc(AiQueryAudit::getId).last("LIMIT 5000"));
        String[] headers = {"审计id", "用户账号", "原始问句", "意图编码", "解析参数",
                "被拒参数", "注入的数据范围", "结果码", "返回行数", "耗时毫秒", "IP", "时间"};
        List<String[]> data = new ArrayList<>();
        for (AiQueryAudit row : rows) {
            data.add(new String[]{
                    str(row.getId()), str(row.getUsername()), str(row.getQuestion()),
                    str(row.getIntentCode()), str(row.getParamsJson()), str(row.getRejectedParamsJson()),
                    str(row.getDataScopeJson()), str(row.getResultCode()), str(row.getRowCount()),
                    str(row.getElapsedMs()), str(row.getIp()),
                    row.getCreatedAt() == null ? "" : row.getCreatedAt().format(TIME_FORMAT)
            });
        }
        return CsvWriter.toCsv(headers, data);
    }

    private LambdaQueryWrapper<AiQueryAudit> buildWrapper(String username, String intentCode, String resultCode,
                                                          LocalDateTime startTime, LocalDateTime endTime) {
        return new LambdaQueryWrapper<AiQueryAudit>()
                .like(username != null && !username.isBlank(), AiQueryAudit::getUsername,
                        username == null ? null : username.trim())
                .eq(intentCode != null && !intentCode.isBlank(), AiQueryAudit::getIntentCode, intentCode)
                .eq(resultCode != null && !resultCode.isBlank(), AiQueryAudit::getResultCode, resultCode)
                .ge(startTime != null, AiQueryAudit::getCreatedAt, startTime)
                .le(endTime != null, AiQueryAudit::getCreatedAt, endTime);
    }

    private AiQueryAuditVO toVO(AiQueryAudit entity) {
        AiQueryAuditVO vo = new AiQueryAuditVO();
        vo.setId(entity.getId());
        vo.setSessionId(entity.getSessionId());
        vo.setUserId(entity.getUserId());
        vo.setUsername(entity.getUsername());
        vo.setQuestion(entity.getQuestion());
        vo.setIntentCode(entity.getIntentCode());
        vo.setParamsJson(entity.getParamsJson());
        vo.setRejectedParamsJson(entity.getRejectedParamsJson());
        vo.setDataScopeJson(entity.getDataScopeJson());
        vo.setResultCode(entity.getResultCode());
        vo.setRowCount(entity.getRowCount());
        vo.setElapsedMs(entity.getElapsedMs());
        vo.setIp(entity.getIp());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
