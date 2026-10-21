package com.zufe.procurement.m4.arrival.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 到货登记请求，功能点 M4-1-1、M4-1-2、M4-2-1。
 * 一次登记可关联多个订单明细行（一次多行）；同一订单可分多次登记（一单多次），
 * 由到货配额逐行校验，不限制张数。
 * 草稿阶段只登记到货数量与验收方式，合格判定在提交时落定（M4-2-3）。
 */
@Data
public class ArrivalSaveRequest {

    /** 来源订单id，必须是已审核且未关闭未作废的订单 */
    private Long orderId;

    private LocalDate arrivalDate;

    private Long warehouseId;

    /** 验收方式：1 免检、2 抽样、3 全检；缺省 2 抽样 */
    private Integer inspectionMode;

    private String remark;

    /** 到货登记行，至少一行 */
    private List<ArrivalItemSaveRequest> items;
}
