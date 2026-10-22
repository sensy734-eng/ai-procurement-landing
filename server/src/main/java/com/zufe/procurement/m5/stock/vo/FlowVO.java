package com.zufe.procurement.m5.stock.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存流水 VO（M5-4-2 查询、M5-4-3 来源回溯）。
 */
@Data
public class FlowVO {

    private Long id;

    private Long materialId;

    private String materialNo;

    private String materialName;

    private String spec;

    private Long warehouseId;

    private String warehouseName;

    private String batchNo;

    private Integer ownerType;

    private String ownerTypeName;

    private Long ownerId;

    private String ownerNo;

    /** 变动类型：1 入库增加、2 作废冲减 */
    private Integer changeType;

    private String changeTypeName;

    private BigDecimal changeQty;

    private BigDecimal beforeQty;

    private BigDecimal afterQty;

    /** 来源单据类型：1 采购入库单 */
    private Integer sourceType;

    private String sourceTypeName;

    private Long sourceId;

    private String sourceNo;

    private String operatorName;

    private LocalDateTime flowTime;

    /** 来源单据摘要（M5-4-3 回溯跳转用） */
    private SourceBrief source;

    @Data
    public static class SourceBrief {

        private String inboundNo;

        private Long orderId;

        private String orderNo;

        private Long inspectionId;

        private String inspectionNo;

        private Integer inboundStatus;

        private String inboundStatusName;
    }
}
