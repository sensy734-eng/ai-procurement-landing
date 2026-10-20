package com.zufe.procurement.m1.warehouse.vo;

import lombok.Data;

/**
 * 仓库使用人展示对象，功能点 M1-3-3。
 */
@Data
public class WarehouseUserVO {

    private Long userId;
    private String username;
    private String realName;
    private String deptName;
    private Integer isDefault;
}
