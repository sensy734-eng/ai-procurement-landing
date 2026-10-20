package com.zufe.procurement.m1.supplier.dto;

import lombok.Data;

/**
 * 供应商列表查询参数，功能点 M1-2-1。
 */
@Data
public class SupplierQuery {

    private long pageNo = 1;
    private long pageSize = 10;
    /** 编码或名称模糊匹配 */
    private String keyword;
    private Integer supplierType;
    private Integer status;
}
