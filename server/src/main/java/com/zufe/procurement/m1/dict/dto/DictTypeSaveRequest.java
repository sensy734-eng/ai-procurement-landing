package com.zufe.procurement.m1.dict.dto;

import lombok.Data;

/**
 * 字典类型保存请求，功能点 M1-4-1。
 * 类型编码创建后不可改：下拉、导入校验、编号前缀都按编码寻址。
 */
@Data
public class DictTypeSaveRequest {

    private String typeCode;
    private String typeName;
    private Integer status;
    private String remark;
}
