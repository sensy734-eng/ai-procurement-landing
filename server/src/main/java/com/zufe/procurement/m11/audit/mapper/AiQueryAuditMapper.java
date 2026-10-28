package com.zufe.procurement.m11.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m11.audit.entity.AiQueryAudit;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 智能查询审计 Mapper，功能点 M11-4-5、M11-4-6。
 *
 * 列表与导出只读；两个分组统计供 P17 的概览卡片使用（结果码分布、意图分布），
 * 数据由 M9 执行时写入，本模块从不改审计内容。
 */
public interface AiQueryAuditMapper extends BaseMapper<AiQueryAudit> {

    @Select("""
            SELECT result_code AS code, COUNT(*) AS cnt FROM ai_query_audit
            WHERE is_deleted = 0 GROUP BY result_code
            """)
    List<Map<String, Object>> countByResultCode();

    @Select("""
            SELECT COALESCE(intent_code, 'unrouted') AS code, COUNT(*) AS cnt FROM ai_query_audit
            WHERE is_deleted = 0 GROUP BY intent_code
            """)
    List<Map<String, Object>> countByIntent();
}
