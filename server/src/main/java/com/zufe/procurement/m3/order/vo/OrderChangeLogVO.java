package com.zufe.procurement.m3.order.vo;

import lombok.Data;

/**
 * 订单变更历史视图，功能点 M3-4-2 时间线呈现。
 * beforeValue/afterValue 为字符串快照，避免视图层解释 Decimal 与日期格式。
 */
@Data
public class OrderChangeLogVO {

    private Long id;

    private Long orderId;

    private String orderNo;

    /** 整单变更时为空 */
    private Long orderItemId;

    private Integer itemNo;

    private String changeField;

    private String changeFieldName;

    private String beforeValue;

    private String afterValue;

    private String changeReason;

    private String operatorName;

    private String changeAt;
}
