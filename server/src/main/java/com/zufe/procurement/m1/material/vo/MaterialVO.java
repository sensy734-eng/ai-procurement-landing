package com.zufe.procurement.m1.material.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料展示对象，功能点 M1-1-1。
 * 分类名称、单位名称、类型名称由 Service 层补全，前端不做字典查询。
 */
@Data
public class MaterialVO {

    private Long id;
    private String materialNo;
    private String materialName;
    private Long categoryId;
    private String categoryNo;
    private String categoryName;
    private String spec;
    private String brand;
    private Long unitId;
    private String unitCode;
    private String unitName;
    private Integer materialType;
    private String materialTypeName;
    private BigDecimal safetyStock;
    private Integer status;
    private String remark;
    private List<ExtAttrVO> extAttrs;
    private LocalDateTime createdAt;

    @Data
    public static class ExtAttrVO {
        private Long id;
        private String attrName;
        private String attrValue;
        private Integer sortNo;
    }
}
