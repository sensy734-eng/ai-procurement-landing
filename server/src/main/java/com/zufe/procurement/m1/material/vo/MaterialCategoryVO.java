package com.zufe.procurement.m1.material.vo;

import lombok.Data;

import java.util.List;

/**
 * 物料分类树节点，功能点 M1-1-7。
 */
@Data
public class MaterialCategoryVO {

    private Long id;
    private String categoryNo;
    private String categoryName;
    private Long parentId;
    private Integer levelNo;
    private Integer sortNo;
    private Integer status;
    private String remark;
    private List<MaterialCategoryVO> children;
}
