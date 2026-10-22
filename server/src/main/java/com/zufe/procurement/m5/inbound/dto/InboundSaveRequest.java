package com.zufe.procurement.m5.inbound.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 入库单新建/编辑请求（M5-1-1）。仅草稿状态可编辑；
 * 明细行整体替换（与订单/验收单的行编辑器同口径）。
 */
@Data
public class InboundSaveRequest {

    /** 入库仓库，必填；库位登记时校验归属（M5-2-2） */
    private Long warehouseId;

    private String inboundDate;

    private String remark;

    /** 明细行，至少一行 */
    private List<Item> items;

    @Data
    public static class Item {

        /** 订单明细行id；手工建单可不带（与订单无关的公共入库） */
        private Long orderItemId;

        private Long materialId;

        private BigDecimal quantity;

        private Long unitId;

        /** 批次号，可空串=无批次管理（M5-2-1） */
        private String batchNo;

        /** 库位id，必须属于本单仓库（M5-2-2） */
        private Long locationId;

        /** 归属类型：1 公共、2 项目、3 工单 */
        private Integer ownerType;

        private Long ownerId;

        private String ownerNo;

        private String remark;
    }
}
