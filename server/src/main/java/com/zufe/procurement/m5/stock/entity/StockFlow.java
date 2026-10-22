package com.zufe.procurement.m5.stock.entity;

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
 * 库存变动流水，对应 stock_flow，功能点 M5-4。
 *
 * 口径：
 * - 只增不改不删（is_deleted 仅兜底字段，业务上不执行删除）；
 * - change_type：1 入库增加、2 作废冲减；change_qty 入库为正、冲减为负；
 * - before_qty / after_qty 记变动前后余额，是 M5-4-4 对账的原始依据；
 * - source_type 固定 1（采购入库单），source_id/source_no 冗余来源单据。
 */
@Data
@TableName("stock_flow")
public class StockFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;

    private Long warehouseId;

    private String batchNo;

    /** 归属类型：1 公共、2 项目、3 工单 */
    private Integer ownerType;

    private Long ownerId;

    private String ownerNo;

    /** 变动类型：1 入库增加、2 作废冲减 */
    private Integer changeType;

    /** 变动数量，入库为正、作废冲减为负 */
    private BigDecimal changeQty;

    private BigDecimal beforeQty;

    private BigDecimal afterQty;

    /** 来源单据类型：1 采购入库单 */
    private Integer sourceType;

    private Long sourceId;

    private String sourceNo;

    private Long operatorId;

    private LocalDateTime flowTime;

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
