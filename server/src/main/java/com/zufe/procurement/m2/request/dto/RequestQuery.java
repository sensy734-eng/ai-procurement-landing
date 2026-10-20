package com.zufe.procurement.m2.request.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 申请单列表检索条件，功能点 M2-1-1。
 * 数据范围由服务层按当前用户注入部门条件，检索条件只承载前端显式筛选。
 */
@Data
public class RequestQuery {

    private long pageNo = 1;
    private long pageSize = 10;

    /** 单号，模糊匹配 */
    private String requestNo;

    /** 单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭 */
    private Integer lifecycleStatus;

    /** 审批结果状态：1 未审核、2 通过、3 驳回 */
    private Integer auditStatus;

    private Long deptId;

    private Long applicantId;

    private LocalDate startDate;

    private LocalDate endDate;
}
