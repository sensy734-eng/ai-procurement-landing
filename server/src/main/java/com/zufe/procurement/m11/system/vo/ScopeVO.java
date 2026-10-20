package com.zufe.procurement.m11.system.vo;

import lombok.Data;

/**
 * 数据范围展示对象，功能点 M11-3-3。
 * scopeValueName 为解析后的可读名称，仓库取名称、部门取字典名称、归属对象取编号本身。
 */
@Data
public class ScopeVO {

    private Long id;
    private Integer subjectType;
    private Long subjectId;
    private Integer scopeDimension;
    private String scopeValue;
    private String scopeValueName;
}
