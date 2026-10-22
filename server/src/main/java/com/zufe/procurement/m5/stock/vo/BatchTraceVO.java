package com.zufe.procurement.m5.stock.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 批次追溯结果（M5-2-3）。
 * 口径：按批次号回溯到入库明细 → 入库单 → 订单 → 供应商，
 * 作废单的明细也返回（批次历史是事实，标注单据状态让用户自己判断）。
 */
@Data
public class BatchTraceVO {

    private String batchNo;

    /** 涉及物料数 */
    private Integer materialCount;

    /** 涉及入库单数 */
    private Integer inboundCount;

    private List<Row> rows;

    @Data
    public static class Row {

        private Long inboundItemId;

        private Long inboundId;

        private String inboundNo;

        /** 入库单状态：1 草稿、2 已确认、3 已作废 */
        private Integer inboundStatus;

        private String inboundStatusName;

        private LocalDate inboundDate;

        private Long materialId;

        private String materialNo;

        private String materialName;

        private BigDecimal quantity;

        private Long warehouseId;

        private String warehouseName;

        private Long orderId;

        private String orderNo;

        private Long supplierId;

        private String supplierName;

        private Long inspectionId;

        private String inspectionNo;
    }
}
