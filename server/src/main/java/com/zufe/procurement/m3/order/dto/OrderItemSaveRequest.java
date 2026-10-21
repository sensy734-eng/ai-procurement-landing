package com.zufe.procurement.m3.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单明细行保存请求，功能点 M3-2-1 新增、M3-2-2 编辑、M3-2-3 交期地点、
 * M3-2-5 归属对象指定。含税金额与不含税金额由服务层计算，不信前端。
 */
@Data
public class OrderItemSaveRequest {

    private Long materialId;

    private BigDecimal quantity;

    /** 含税单价 */
    private BigDecimal unitPrice;

    /** 税率，缺省 0.13 */
    private BigDecimal taxRate;

    /** 行交货期，缺省取主表 */
    private LocalDate deliveryDate;

    private String deliveryPlace;

    /** 归属类型：1 公共、2 项目、3 工单，缺省公共 */
    private Integer ownerType;

    private Long ownerId;

    private String remark;
}
