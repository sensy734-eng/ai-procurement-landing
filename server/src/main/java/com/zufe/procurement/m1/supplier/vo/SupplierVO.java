package com.zufe.procurement.m1.supplier.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商展示对象，功能点 M1-2-1。
 * 类型名称与各从表计数由 Service 层补全。
 */
@Data
public class SupplierVO {

    private Long id;
    private String supplierNo;
    private String supplierName;
    private Integer supplierType;
    private String supplierTypeName;
    private String taxNo;
    private String address;
    private String phone;
    private String email;
    private String bankName;
    private String bankAccount;
    private Integer status;
    private String remark;
    private int contactCount;
    private String defaultContactName;
    private int scopeCount;
    private int attachmentCount;
    private LocalDateTime createdAt;
}
