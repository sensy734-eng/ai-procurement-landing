package com.zufe.procurement.m1.warehouse.vo;

import lombok.Data;

/**
 * 库位展示对象，功能点 M1-3-2。
 */
@Data
public class LocationVO {

    private Long id;
    private Long warehouseId;
    private String locationNo;
    private String locationName;
    private Integer status;
    private String remark;
}
