package com.zufe.procurement.m1.dict.dto;

import lombok.Data;

/**
 * 字典项保存请求，功能点 M1-4-1、M1-4-2。
 * 项编码创建后不可改；bill_type 的 ext_value 是单据编号前缀（M1-4-2），
 * supplier_type 的项编码与 supplier.supplier_type 取值保持一致。
 */
@Data
public class DictItemSaveRequest {

    private Long typeId;
    private String itemCode;
    private String itemName;
    private Long parentId;
    private Integer sortNo;
    private Integer status;
    private String extValue;
    private String remark;
}
