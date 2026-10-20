package com.zufe.procurement.m1.material.dto;

import lombok.Data;

/**
 * 物料分类保存请求，功能点 M1-1-7。
 * 编码创建后不可改；允许改上级，但不能是自身或子孙节点。
 */
@Data
public class MaterialCategorySaveRequest {

    private String categoryNo;
    private String categoryName;
    private Long parentId;
    private Integer sortNo;
    private Integer status;
    private String remark;
}
