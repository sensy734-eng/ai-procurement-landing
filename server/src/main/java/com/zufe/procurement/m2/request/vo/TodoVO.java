package com.zufe.procurement.m2.request.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 我的待办视图，功能点 M2-3-8。
 * 一条待办对应一个审批任务；单据信息冗余展示，避免前端二次查询。
 */
@Data
public class TodoVO {

    private Long taskId;

    private String taskNo;

    /** 1 采购申请、2 采购订单 */
    private Integer billType;

    private Long billId;

    private String billNo;

    private Integer nodeSeq;

    private String nodeName;

    /** 1 或签、2 会签 */
    private Integer signType;

    private String signTypeName;

    private LocalDateTime createdTime;

    /** 单据摘要，便于待办列表直接展示 */
    private String applicantName;

    private LocalDate requestDate;

    private BigDecimal totalAmount;

    private Integer itemCount;
}
