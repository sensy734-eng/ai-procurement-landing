package com.zufe.procurement.m4.arrival.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 由验收单生成入库单草稿的请求，功能点 M4-3-1 至 M4-3-4。
 * 仅已验收单可生成；每次生成一张独立入库单草稿（多批次 M4-3-3），
 * 可多次生成直到可入量耗尽（部分入库 M4-3-2）。
 * 行级入库量缺省取每行全部可入量；显式传量时逐行校验不超过可入量，
 * 且累计已生成量 + 本次量不超过订单行剩余入库配额（M4-3-4）。
 */
@Data
public class InboundGenerateRequest {

    private LocalDate inboundDate;

    /** 行级入库量，缺省取每行全部可入量 */
    private List<InboundItemQty> items;

    private String remark;

    /** 行级入库量 */
    @Data
    public static class InboundItemQty {

        /** 验收明细行id，必须属于该验收单 */
        private Long inspectionItemId;

        /** 本次入库数量，大于 0 且不超过该行可入量 */
        private java.math.BigDecimal quantity;
    }
}
