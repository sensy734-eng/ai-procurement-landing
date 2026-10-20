package com.zufe.procurement.m2.request.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 申请明细行视图。展示字段（物料编号、单位、建议供应商、归属对象名称）
 * 由服务层联表补全，前端不二次查询。
 */
@Data
public class RequestItemVO {

    private Long id;

    private Integer itemNo;

    private Long materialId;

    private String materialNo;

    private String materialName;

    private String spec;

    private BigDecimal quantity;

    private Long unitId;

    private String unitName;

    private BigDecimal estPrice;

    private BigDecimal estAmount;

    private Long suggestSupplierId;

    private String suggestSupplierName;

    private LocalDate requireDate;

    /** 归属类型：1 公共、2 项目、3 工单 */
    private Integer ownerType;

    private Long ownerId;

    private String ownerNo;

    private String ownerName;

    private BigDecimal transferredQty;

    /** 剩余可转数量 = 申请数量 − 已转数量，服务层计算 */
    private BigDecimal remainingQty;

    /** 行状态：1 待转、2 部分已转、3 已转完、4 已退回、5 已关闭 */
    private Integer itemStatus;

    private String itemStatusName;

    private String remark;
}
