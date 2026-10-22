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
 * 库存余额，对应 stock_balance，功能点 M5-3。
 *
 * 口径：
 * - quantity 是库存数量的唯一落点，只由入库单确认（R2）与作废冲减维护，
 *   任何其他路径不得直接更新本表；
 * - 可用量不落库（R3）：可用量 = quantity − frozen_qty − reserved_qty，
 *   由查询层现算，本表不存可用量列；
 * - 公共库存 owner_id=0 / owner_no=PUBLIC，与 DDL 唯一键约定一致；
 * - version 乐观锁：并发入库确认同一行时后到者冲突重试（M5-5-4）。
 */
@Data
@TableName("stock_balance")
public class StockBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;

    private Long warehouseId;

    /** 批次号，无批次管理时为空串（唯一键组成部分） */
    private String batchNo;

    /** 归属类型：1 公共、2 项目、3 工单 */
    private Integer ownerType;

    /** 归属对象id，公共库存固定 0 */
    private Long ownerId;

    /** 归属对象编号，公共库存固定 PUBLIC */
    private String ownerNo;

    /** 库存数量，唯一由入库单确认/作废冲减维护 */
    private BigDecimal quantity;

    private BigDecimal frozenQty;

    private BigDecimal reservedQty;

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
