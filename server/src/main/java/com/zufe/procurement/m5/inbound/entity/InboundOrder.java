package com.zufe.procurement.m5.inbound.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购入库单，对应 inbound_order，功能点 M5-1（轮次 7 完整维护）。
 * 轮次 6 只落地「由验收单生成草稿」这一段（M4-3）：从已验收单自动创建，
 * status=1 草稿，确认（status=2）与库存写入在轮次 7。
 *
 * 口径：确认动作是库存数量的唯一写入点（R2），所以草稿不写 delivered_qty；
 * 但草稿要占入库配额——累计已生成未确认的量也计入「已生成」，
 * 否则同一验收单可被反复生成草稿超发。
 */
@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inboundNo;

    /** 来源验收单id，由验收单生成时必填 */
    private Long inspectionId;

    private String inspectionNo;

    private Long orderId;

    private String orderNo;

    private Long supplierId;

    private Long warehouseId;

    private LocalDate inboundDate;

    /** 入库单状态：1 草稿、2 已确认、3 已作废 */
    private Integer status;

    private LocalDateTime confirmTime;

    private Long confirmBy;

    private LocalDateTime voidTime;

    private String voidReason;

    private Long operatorId;

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
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
