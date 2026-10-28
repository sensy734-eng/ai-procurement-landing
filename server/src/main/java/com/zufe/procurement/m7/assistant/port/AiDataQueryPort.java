package com.zufe.procurement.m7.assistant.port;

import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m7.assistant.vo.DataAnswer;

import java.util.Map;

/**
 * 受控数据查询端口（M9 能力边界）。
 *
 * M7 只依赖这个接口，不认识 M9 的具体实现：
 * 端口把「模型只输出意图与参数、参数经白名单校验、由后端服务执行」
 * 这条受控边界固化在编译期——M7 无处可写 SQL。
 */
public interface AiDataQueryPort {

    /**
     * @param question         原始问句
     * @param overrideIntentCode 人工指定的意图，可空
     * @param overrideParams   人工修正的参数，可空
     * @param confirmedEntityId 澄清选项里选定的实体 id，可空
     * @param context          上一轮上下文，可空
     */
    DataAnswer query(String question, String overrideIntentCode, Map<String, Object> overrideParams,
                     Long confirmedEntityId, AiContext context);
}
