package com.zufe.procurement.m11.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.CsvWriter;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.m11.system.entity.SysOperationLog;
import com.zufe.procurement.m11.system.mapper.SysOperationLogMapper;
import com.zufe.procurement.m11.system.vo.OperationLogVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务。覆盖功能点：
 * M11-4-4 操作日志检索；同时承担 M11-3-5 授权变更留痕的写入。
 * 日志写入失败不影响主流程，只打错误日志，避免审计设施成为业务故障点。
 */
@Slf4j
@Service
public class OperationLogService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysOperationLogMapper logMapper;

    public OperationLogService(SysOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /**
     * 记录一次操作。detail 建议传修改前后值，便于回溯。
     */
    public void record(String moduleCode, String action, String targetType,
                       Long targetId, String targetNo, String detail) {
        try {
            SysOperationLog entity = new SysOperationLog();
            CurrentUser currentUser = AuthContext.get();
            if (currentUser != null) {
                entity.setUserId(currentUser.getUserId());
                entity.setUsername(currentUser.getUsername());
                entity.setRealName(currentUser.getRealName());
            }
            entity.setModuleCode(moduleCode);
            entity.setAction(action);
            entity.setTargetType(targetType);
            entity.setTargetId(targetId);
            entity.setTargetNo(targetNo);
            entity.setDetail(detail);
            entity.setResultCode("Success");
            entity.setIp(currentIp());
            logMapper.insert(entity);
        } catch (Exception e) {
            log.error("操作日志写入失败 module={} action={}", moduleCode, action, e);
        }
    }

    /**
     * M11-4-4 操作日志检索。按操作人、模块、动作与时间范围过滤。
     */
    public PageResult<OperationLogVO> page(long pageNo, long pageSize, String username,
                                           String moduleCode, String action,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        Page<SysOperationLog> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100));
        Page<SysOperationLog> result = logMapper.selectPage(page, buildWrapper(username, moduleCode, action,
                startTime, endTime).orderByDesc(SysOperationLog::getId));
        List<OperationLogVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 日志导出，CSV 格式，UTF-8 BOM，Excel 可直接打开。
     */
    public byte[] exportCsv(String username, String moduleCode, String action,
                            LocalDateTime startTime, LocalDateTime endTime) {
        List<SysOperationLog> rows = logMapper.selectList(buildWrapper(username, moduleCode, action,
                startTime, endTime).orderByDesc(SysOperationLog::getId).last("LIMIT 5000"));
        String[] headers = {"日志id", "操作人账号", "操作人姓名", "模块", "动作", "对象类型",
                "对象id", "对象编号", "详情", "结果码", "IP", "时间"};
        List<String[]> data = new ArrayList<>();
        for (SysOperationLog row : rows) {
            data.add(new String[]{
                    str(row.getId()), str(row.getUsername()), str(row.getRealName()),
                    str(row.getModuleCode()), str(row.getAction()), str(row.getTargetType()),
                    str(row.getTargetId()), str(row.getTargetNo()), str(row.getDetail()),
                    str(row.getResultCode()), str(row.getIp()),
                    row.getCreatedAt() == null ? "" : row.getCreatedAt().format(TIME_FORMAT)
            });
        }
        return CsvWriter.toCsv(headers, data);
    }

    private LambdaQueryWrapper<SysOperationLog> buildWrapper(String username, String moduleCode, String action,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        return new LambdaQueryWrapper<SysOperationLog>()
                .like(username != null && !username.isBlank(), SysOperationLog::getUsername,
                        username == null ? null : username.trim())
                .eq(moduleCode != null && !moduleCode.isBlank(), SysOperationLog::getModuleCode, moduleCode)
                .eq(action != null && !action.isBlank(), SysOperationLog::getAction, action)
                .ge(startTime != null, SysOperationLog::getCreatedAt, startTime)
                .le(endTime != null, SysOperationLog::getCreatedAt, endTime);
    }

    private OperationLogVO toVO(SysOperationLog entity) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setRealName(entity.getRealName());
        vo.setModuleCode(entity.getModuleCode());
        vo.setAction(entity.getAction());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setTargetNo(entity.getTargetNo());
        vo.setDetail(entity.getDetail());
        vo.setResultCode(entity.getResultCode());
        vo.setIp(entity.getIp());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
