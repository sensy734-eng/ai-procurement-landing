package com.zufe.procurement.m4.arrival.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 验收记录检索条件，功能点 M4-4-1。
 * 数据范围由服务层按当前用户注入仓库条件（仓管员按仓库维度授权），
 * 检索条件只承载前端显式筛选。
 */
@Data
public class ArrivalQuery {

    private long pageNo = 1;
    private long pageSize = 10;

    /** 验收单号，模糊匹配 */
    private String inspectionNo;

    /** 订单号，模糊匹配 */
    private String orderNo;

    private Long orderId;

    private Long supplierId;

    private Long warehouseId;

    /** 验收结果：1 合格、2 不合格、3 让步接收 */
    private Integer inspectionResult;

    /** 验收单状态：1 草稿、2 已验收 */
    private Integer status;

    /** 到货日期范围 */
    private LocalDate startDate;

    private LocalDate endDate;
}
