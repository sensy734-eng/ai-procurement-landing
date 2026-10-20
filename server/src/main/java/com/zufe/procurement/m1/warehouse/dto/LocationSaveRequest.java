package com.zufe.procurement.m1.warehouse.dto;

import lombok.Data;

/**
 * 库位保存请求，功能点 M1-3-2。
 */
@Data
public class LocationSaveRequest {

    private String locationNo;
    private String locationName;
    private Integer status;
    private String remark;
}
