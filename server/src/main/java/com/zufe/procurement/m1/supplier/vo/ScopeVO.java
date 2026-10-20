package com.zufe.procurement.m1.supplier.vo;

import lombok.Data;

/**
 * 供应商供货范围展示对象，功能点 M1-2-6。
 * scopeName 为物料名称或分类名称，由 Service 层补全。
 */
@Data
public class ScopeVO {

    private Long id;
    private Integer scopeType;
    private Long scopeId;
    private String scopeName;
    private String remark;
}
