package com.zufe.procurement.m2.request.dto;

import lombok.Data;

import java.util.List;

/**
 * 按行退回请求，功能点 M2-2-4。
 * 拆分口径：被退回行从原单剥离生成新的驳回态草稿单（退回部分），
 * 原单剩余行继续审批（通过部分）；剩余行为零时原单整体视为驳回。
 */
@Data
public class ReturnItemsRequest {

    /** 被退回的明细行id，至少一行，且必须是未转完、未退回、未关闭的行 */
    private List<Long> itemIds;

    /** 退回意见，必填 */
    private String opinion;
}
