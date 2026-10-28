package com.zufe.procurement.m7.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m7.assistant.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /** 引用覆盖率统计（M8-3-4）：有效回答数与其中带引用的条数 */
    @Select("""
            SELECT COUNT(*) FROM ai_message
            WHERE role = 2 AND msg_type IN (1, 2) AND result_code = 'Success' AND is_deleted = 0
            """)
    long countEffectiveAnswers();

    @Select("""
            SELECT COUNT(*) FROM ai_message
            WHERE role = 2 AND msg_type IN (1, 2) AND result_code = 'Success' AND is_deleted = 0
              AND ref_doc_ids IS NOT NULL AND ref_doc_ids <> ''
            """)
    long countCitedAnswers();

    /** 拒答条数（M8-5-3）：msg_type=5 为拒答回答 */
    @Select("""
            SELECT COUNT(*) FROM ai_message
            WHERE role = 2 AND msg_type = 5 AND is_deleted = 0
            """)
    long countRefusals();
}
