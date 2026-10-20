package com.zufe.procurement.m1.warehouse.vo;

import lombok.Data;

/**
 * 仓库展示对象，功能点 M1-3-1。
 * 负责人姓名与库位、使用人计数由 Service 层补全。
 */
@Data
public class WarehouseVO {

    private Long id;
    private String warehouseNo;
    private String warehouseName;
    private Long managerId;
    private String managerName;
    private String address;
    private String phone;
    private Integer status;
    private String remark;
    private int locationCount;
    private int userCount;
}
