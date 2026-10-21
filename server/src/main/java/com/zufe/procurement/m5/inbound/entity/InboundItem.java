package com.zufe.procurement.m5.inbound.entity;

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
 * 采购入库明细，对应 inbound_item，功能点 M5-1、M5-2（轮次 7 完整维护）。
 * 轮次 6 由验收单生成草稿时带入：quantity 取验收合格数量，
 * 让步接收单取合格 + 不合格（不合格不生成入库的唯一例外）；
 * 批次号与库位在轮次 7 维护，此处批次号从验收预登记带入，库位置空。
 */
@Data
@TableName("inbound_item")
public class InboundItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inboundId;

    /** 订单明细行id，由验收单生成时必填 */
    private Long orderItemId;

    private Long materialId;

    /** 入库数量，取验收合格数量；让步接收单含不合格部分 */
    private BigDecimal quantity;

    private Long unitId;

    /** 批次号，无批次管理时为空串（M5-2-1），此处从验收预登记带入 */
    private String batchNo;

    private Long locationId;

    /** 归属类型：1 公共、2 项目、3 工单，继承验收行 */
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
