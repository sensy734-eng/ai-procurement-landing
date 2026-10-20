package com.zufe.procurement.m1.dict.vo;

import lombok.Data;

/**
 * 字典项展示对象，M1-4-1。
 */
@Data
public class DictItemVO {

    private Long id;
    private String itemCode;
    private String itemName;
    private Long parentId;
    private Integer sortNo;
    private String extValue;
}
