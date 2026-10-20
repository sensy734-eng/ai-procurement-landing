package com.zufe.procurement.m2.request.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购申请明细行，对应 purchase_request_item，功能点 M2-2、M2-4-3。
 * 行状态：1 待转、2 部分已转、3 已转完、4 已退回、5 已关闭。
 * transferred_qty 已转数量的唯一写入点是转订单动作（M2-4-3），
 * 到货与入库不回写本字段（与 R2 库存唯一写入点同构）。
 * 注意：本表无 updated_by 列，与 A 域表一致，勿按 platform 域习惯建模。
 */
@Data
@TableName("purchase_request_item")
public class PurchaseRequestItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requestId;

    /** 行号，单内唯一，保存时按当前最大行号递增 */
    private Integer itemNo;

    private Long materialId;

    private BigDecimal quantity;

    /** 计量单位，保存时取物料档案的 unit_id，不允许行级自选，避免同物异单位 */
    private Long unitId;

    /** 预估单价，可空；取参考价或人工填写，仅用于预估金额 */
    private BigDecimal estPrice;

    /** 预估金额 = 数量 × 预估单价，服务层计算，不信任前端传值 */
    private BigDecimal estAmount;

    /** 建议供应商，可空（M2-2-3）；转订单时按此字段分组（M2-4-2） */
    private Long suggestSupplierId;

    private LocalDate requireDate;

    /** 归属类型：1 公共、2 项目、3 工单 */
    private Integer ownerType;

    private Long ownerId;

    /** 归属对象编号，冗余，便于不联表查询 */
    private String ownerNo;

    /** 已转订单数量，由转订单动作回写（M2-4-3） */
    private BigDecimal transferredQty;

    private Integer itemStatus;

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
