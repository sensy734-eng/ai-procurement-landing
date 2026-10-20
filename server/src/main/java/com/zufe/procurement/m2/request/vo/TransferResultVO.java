package com.zufe.procurement.m2.request.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 转订单结果视图，功能点 M2-4-1、M2-4-2。
 * 同一供应商的多行合并为一张订单草稿，按供应商逐个返回，
 * 前端可逐张跳转编辑（完整订单维护在 M3 实现）。
 */
@Data
public class TransferResultVO {

    private List<TransferredOrder> orders = new ArrayList<>();

    /** 参与转单的申请单号，用于操作日志与界面提示 */
    private List<String> requestNos = new ArrayList<>();

    @Data
    public static class TransferredOrder {

        private Long orderId;

        private String orderNo;

        private Long supplierId;

        private String supplierName;

        /** 本单转入的明细行数 */
        private int itemCount;

        private BigDecimal totalQty;

        private BigDecimal totalAmount;

        /** 来源申请单号，多单合并时以逗号连接 */
        private String sourceRequestNo;
    }
}
