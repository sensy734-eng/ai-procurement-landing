package com.zufe.procurement.m1.supplier.dto;

import lombok.Data;

import java.util.List;

/**
 * 供应商保存请求，功能点 M1-2-2。
 * 编码创建后不可改；联系人与供货范围走各自独立接口维护。
 */
@Data
public class SupplierSaveRequest {

    private String supplierNo;
    private String supplierName;
    private Integer supplierType;
    private String taxNo;
    private String address;
    private String phone;
    private String email;
    private String bankName;
    private String bankAccount;
    private String remark;
}
