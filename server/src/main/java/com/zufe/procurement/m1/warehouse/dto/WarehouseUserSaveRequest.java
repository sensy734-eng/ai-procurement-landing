package com.zufe.procurement.m1.warehouse.dto;

import lombok.Data;

import java.util.List;

/**
 * 仓库使用人分配请求，功能点 M1-3-3。
 * 整体替换：传入全量使用人，默认仓库必须在列表中且至多一个。
 */
@Data
public class WarehouseUserSaveRequest {

    private List<Long> userIds;
    private Long defaultUserId;
}
