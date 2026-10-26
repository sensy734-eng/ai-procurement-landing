package com.zufe.procurement.m3.order.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 订单列表检索条件，功能点 M3-1-1。
 * 数据范围由服务层按当前用户注入部门条件，检索条件只承载前端显式筛选。
 * overdue 为 M3-5-4 超期未交付筛选：交货期已过且未交付完的订单。
 */
@Data
public class OrderQuery {

    private long pageNo = 1;
    private long pageSize = 10;

    /** 订单号，模糊匹配 */
    private String orderNo;

    private Long supplierId;

    /** 单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废 */
    private Integer lifecycleStatus;

    /** 审批结果状态：1 未审核、2 通过、3 驳回 */
    private Integer auditStatus;

    /** 业务履约状态：1 待交付、2 部分交付、3 全部交付、4 已关闭 */
    private Integer fulfillStatus;

    private Long deptId;

    private Long buyerId;

    /** 下单日期范围 */
    private LocalDate startDate;

    private LocalDate endDate;

    /** M3-5-4 超期未交付筛选，超期天数由服务层按当前日期计算 */
    private Boolean overdue;

    /** M3-6 整单偏离等级筛选：1 关注、2 显著（不传为全部） */
    private Integer deviationLevel;
}
