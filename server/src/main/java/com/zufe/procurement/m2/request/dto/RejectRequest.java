package com.zufe.procurement.m2.request.dto;

import lombok.Data;

/**
 * 审批驳回请求，功能点 M2-3-3。驳回意见必填，
 * 与按行退回共用同一实体（M2-2-4 同样要求填写退回意见）。
 */
@Data
public class RejectRequest {

    private String opinion;
}
