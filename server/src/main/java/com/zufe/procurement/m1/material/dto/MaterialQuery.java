package com.zufe.procurement.m1.material.dto;

import lombok.Data;

/**
 * 物料列表查询参数，功能点 M1-1-1。
 */
@Data
public class MaterialQuery {

    private long pageNo = 1;
    private long pageSize = 10;
    /** 编码或名称模糊匹配 */
    private String keyword;
    private Long categoryId;
    private Integer status;
    private Integer materialType;
}
