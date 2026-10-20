package com.zufe.procurement.m1.warehouse.dto;

import lombok.Data;

/**
 * 仓库保存请求，功能点 M1-3-1。
 */
@Data
public class WarehouseSaveRequest {

    private String warehouseNo;
    private String warehouseName;
    private Long managerId;
    private String address;
    private String phone;
    private String remark;
}
