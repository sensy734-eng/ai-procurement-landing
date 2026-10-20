package com.zufe.procurement.m2.request.dto;

import lombok.Data;

import java.util.List;

/**
 * 申请转订单请求，功能点 M2-4-1 按行选择转订单、M2-4-2 批量转订单。
 * 两种入口共用同一结构：sources 列出参与转单的申请单及勾选的明细行，
 * 服务层按建议供应商分组，同一供应商的多行合并为一张订单草稿。
 */
@Data
public class TransferRequest {

    private List<TransferSource> sources;

    @Data
    public static class TransferSource {

        private Long requestId;

        /** 勾选的明细行id，为空表示该单全部可转行 */
        private List<Long> itemIds;
    }
}
