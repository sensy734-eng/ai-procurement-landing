package com.zufe.procurement.m1.dict.vo;

import lombok.Data;

/**
 * 字典项展示对象，M1-4-1。
 * 下拉消费只读编码与名称；管理端 additionally 需要状态、备注与所属类型。
 */
@Data
public class DictItemVO {

    private Long id;
    private Long typeId;
    private String itemCode;
    private String itemName;
    private Long parentId;
    private Integer sortNo;
    private Integer status;
    private String extValue;
    private String remark;
}
