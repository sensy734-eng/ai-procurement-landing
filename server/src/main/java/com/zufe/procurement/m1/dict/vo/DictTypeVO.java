package com.zufe.procurement.m1.dict.vo;

import lombok.Data;

/**
 * 字典类型展示对象，功能点 M1-4-1。
 */
@Data
public class DictTypeVO {

    private Long id;
    private String typeCode;
    private String typeName;
    private Integer status;
    private String remark;
    private long itemCount;
}
