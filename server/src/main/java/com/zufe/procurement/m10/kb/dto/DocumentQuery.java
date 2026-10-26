package com.zufe.procurement.m10.kb.dto;

import lombok.Data;

/** 知识文档分页查询（M10-4-1） */
@Data
public class DocumentQuery {

    private String docName;
    /** 文档类型：1 制度、2 规范、3 操作手册、4 流程说明 */
    private Integer docType;
    /** 状态：1 启用、2 停用、3 失效 */
    private Integer status;
    /** 解析状态：1 待解析、2 解析中、3 解析成功、4 解析失败 */
    private Integer parseStatus;
    private long pageNo = 1;
    private long pageSize = 10;
}
