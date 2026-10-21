package com.zufe.procurement.m4.arrival.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 到货验收明细行，对应 arrival_inspection_item，功能点 M4-1-2、M4-2-3。
 * 守恒律 R12：合格数量 + 不合格数量 = 本次到货数量，服务层在登记与提交时校验。
 * 批次号在此预登记（M5-2-1），生成入库单时带入 inbound_item。
 * 注意：本表无 updated_by 列，与 purchase_order_item 相同。
 */
@Data
@TableName("arrival_inspection_item")
public class ArrivalInspectionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inspectionId;

    /** 订单明细行id，到货配额与入库配额都按此行累计 */
    private Long orderItemId;

    private Long materialId;

    private Long unitId;

    /** 本次到货数量，校验不超过订单行未到货量（M4-1-3） */
    private BigDecimal arrivalQty;

    private BigDecimal qualifiedQty;

    private BigDecimal unqualifiedQty;

    /** 预登记批次号（M5-2-1） */
    private String batchNo;

    /** 归属类型，继承订单行：1 公共、2 项目、3 工单 */
    private Integer ownerType;

    private Long ownerId;

    private String ownerNo;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
