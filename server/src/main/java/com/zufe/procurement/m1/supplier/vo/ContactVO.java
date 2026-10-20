package com.zufe.procurement.m1.supplier.vo;

import lombok.Data;

/**
 * 供应商联系人展示对象，功能点 M1-2-4。
 */
@Data
public class ContactVO {

    private Long id;
    private String contactName;
    private String position;
    private String phone;
    private String email;
    private Integer isDefault;
    private Integer status;
    private String remark;
}
