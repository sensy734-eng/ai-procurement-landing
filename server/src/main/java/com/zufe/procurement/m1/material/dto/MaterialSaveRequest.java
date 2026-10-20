package com.zufe.procurement.m1.material.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料保存请求，功能点 M1-1-2 新增、M1-1-3 编辑。
 * 编码创建后不可改；扩展属性随主档整体保存（M1-1-6）。
 */
@Data
public class MaterialSaveRequest {

    private String materialNo;

    private String materialName;

    private Long categoryId;

    private String spec;

    private String brand;

    private Long unitId;

    private Integer materialType;

    private BigDecimal safetyStock;

    private String remark;

    /** 扩展属性名值对，保存时整体替换 */
    private List<ExtAttrItem> extAttrs;

    @Data
    public static class ExtAttrItem {
        private String attrName;
        private String attrValue;
        private Integer sortNo;
    }
}
