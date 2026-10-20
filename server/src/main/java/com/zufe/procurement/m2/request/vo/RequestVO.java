package com.zufe.procurement.m2.request.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 申请单详情视图，功能点 M2-1-1、M2-3-6。
 * 三状态分三个独立字段返回（lifecycleStatus / auditStatus），
 * 各自带名称字段，前端不得合并展示；审批历史按节点序排列，时间线呈现。
 */
@Data
public class RequestVO {

    private Long id;

    private String requestNo;

    private LocalDate requestDate;

    private Long deptId;

    private String deptName;

    private Long applicantId;

    private String applicantName;

    private LocalDate requireDate;

    private BigDecimal totalAmount;

    /** 单据生命周期状态：1 草稿、2 已提交、3 已审核、4 已关闭、5 已作废 */
    private Integer lifecycleStatus;

    private String lifecycleStatusName;

    /** 审批结果状态：1 未审核、2 通过、3 驳回 */
    private Integer auditStatus;

    private String auditStatusName;

    private String closeReason;

    private String remark;

    private List<RequestItemVO> items = new ArrayList<>();

    /** 审批历史（M2-3-6），按节点序与处理时间排列 */
    private List<ApprovalHistoryVO> approvalHistory = new ArrayList<>();

    /** 明细行合计（仅状态 1/2/3 的活跃行），服务层计算 */
    private int activeItemCount;

    /** 剩余可转数量合计，服务层计算，供关闭确认提示（M2-4-4） */
    private BigDecimal remainingQtyTotal;
}
