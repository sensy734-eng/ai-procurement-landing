package com.zufe.procurement.m5.inbound.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单 VO（M5-1-2 列表/详情、M5-1-5 溯源）。
 * 详情带明细行、来源链（验收单 → 订单 → 申请单）与库存流水摘要；
 * 列表只带主表字段与明细合计。
 */
@Data
public class InboundVO {

    private Long id;

    private String inboundNo;

    private Long inspectionId;

    private String inspectionNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private Long warehouseId;

    private String warehouseName;

    private LocalDate inboundDate;

    /** 状态：1 草稿、2 已确认、3 已作废 */
    private Integer status;

    private String statusName;

    private LocalDateTime confirmTime;

    private Long confirmBy;

    private String confirmByName;

    private LocalDateTime voidTime;

    private String voidReason;

    private String remark;

    /** 明细合计数量（Service 现算） */
    private BigDecimal totalQty;

    private List<Item> items;

    /** 详情：来源链，手工单（无来源）为 null */
    private TraceChain trace;

    /** 详情：本单产生的库存流水（入库记录 + 可能的冲减记录） */
    private List<FlowBrief> flows;

    private LocalDateTime createdAt;

    private String createdByName;

    @Data
    public static class Item {

        private Long id;

        private Long orderItemId;

        private Long materialId;

        private String materialNo;

        private String materialName;

        private String spec;

        private BigDecimal quantity;

        private Long unitId;

        private String unitName;

        private String batchNo;

        private Long locationId;

        private String locationNo;

        private String locationName;

        private Integer ownerType;

        private Long ownerId;

        private String ownerNo;

        private String remark;
    }

    /** 来源链：验收单 → 订单 → 申请单，逐级可能有值 */
    @Data
    public static class TraceChain {

        private Long inboundId;

        private String inboundNo;

        private Long inspectionId;

        private String inspectionNo;

        private Long orderId;

        private String orderNo;

        private Long requestId;

        private String requestNo;

        private String applicantName;

        private String deptName;
    }

    /** 流水摘要（来源单据回溯 M5-4-3 的列表态） */
    @Data
    public static class FlowBrief {

        private Long id;

        private String batchNo;

        private Integer changeType;

        private String changeTypeName;

        private BigDecimal changeQty;

        private BigDecimal beforeQty;

        private BigDecimal afterQty;

        private LocalDateTime flowTime;

        private String operatorName;
    }
}
