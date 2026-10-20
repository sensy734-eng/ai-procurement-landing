package com.zufe.procurement.m2.request.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 申请明细行保存请求，功能点 M2-2-1 新增、M2-2-2 编辑、M2-2-3 建议供应商、
 * M2-2-5 归属对象。计量单位不接收前端传值，服务层取物料档案的 unit_id，
 * 避免同物异单位；预估金额由服务层按数量乘单价计算，不信任前端传值。
 */
@Data
public class RequestItemSaveRequest {

    private Long materialId;

    /** 申请数量，必须大于 0 */
    private BigDecimal quantity;

    /** 预估单价，可空；为空时预估金额记 0 */
    private BigDecimal estPrice;

    /** 建议供应商，可空；非空时校验供应商启用与供货范围（M2-2-3） */
    private Long suggestSupplierId;

    /** 行需求日期，必填 */
    private LocalDate requireDate;

    /** 归属类型：1 公共、2 项目、3 工单；1 时归属对象可空 */
    private Integer ownerType;

    /** 归属对象id，owner_type 为 2/3 时必填 */
    private Long ownerId;

    private String remark;
}
