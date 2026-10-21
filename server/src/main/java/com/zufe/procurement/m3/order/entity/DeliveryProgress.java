package com.zufe.procurement.m3.order.entity;

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
 * 交付进度登记，对应 delivery_progress，功能点 M3-5-1。
 * 过程记录，用于跟单与超期提醒；不参与已交付数量与履约状态判定——
 * delivered_qty 的唯一写入点是采购入库单确认（R2），本表只写流水。
 * 注意：本表无 updated_by 列。
 */
@Data
@TableName("delivery_progress")
public class DeliveryProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 订单号，冗余 */
    private String orderNo;

    private Long orderItemId;

    /** 行号，冗余 */
    private Integer itemNo;

    /** 物料id，冗余，便于按物料筛选 */
    private Long materialId;

    /** 本次登记交付数量，仅供跟单参考 */
    private BigDecimal deliveryQty;

    private LocalDate deliveryDate;

    /** 供应商批次说明，可选 */
    private String batchNo;

    private String remark;

    private Long operatorId;

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
