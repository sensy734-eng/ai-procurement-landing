package com.zufe.procurement.m3.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购订单明细行视图，功能点 M3-2、M3-5。
 * deliveredQty 已交付数量（即已入库数量）只读展示，唯一写入点是入库单确认；
 * remainingQty 剩余数量不落库，由服务层计算（quantity − deliveredQty）。
 */
@Data
public class OrderItemVO {

    private Long id;

    private Integer itemNo;

    private Long materialId;

    private String materialNo;

    private String materialName;

    private String spec;

    private BigDecimal quantity;

    private Long unitId;

    private String unitName;

    private BigDecimal unitPrice;

    private BigDecimal taxRate;

    private BigDecimal amount;

    private BigDecimal noTaxAmount;

    private String deliveryDate;

    private String deliveryPlace;

    private BigDecimal deliveredQty;

    private BigDecimal remainingQty;

    private Integer ownerType;

    private Long ownerId;

    private String ownerNo;

    private String ownerName;

    private Integer itemFulfillStatus;

    private String itemFulfillStatusName;

    /** 来源申请明细行id，支撑按行转订单的多对多追溯（M2-4-1） */
    private Long sourceRequestItemId;

    /** 行级超期天数：行交货期（缺省取主表）已过且未交付完，后端计算 */
    private Integer overdueDays;

    /** 偏离率（单价 − 参考价）/ 参考价，保存时按当时生效参考价计算；无参考价为 null */
    private BigDecimal deviationRate;

    /** 偏离等级：0 正常、1 关注、2 显著（阈值由 sys_param 价格分组固化） */
    private Integer deviationLevel;

    /** 偏离等级名称：正常 / 关注 / 显著 */
    private String deviationLevelName;

    private String remark;
}
