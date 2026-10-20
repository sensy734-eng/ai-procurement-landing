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
 * 采购订单明细行，对应 purchase_order_item，功能点 M3-2。
 * delivered_qty 已交付数量的唯一写入点是入库单确认（R2），
 * 交付进度登记只写 delivery_progress，不改此字段。
 * source_request_item_id 支撑按行转订单的多对多追溯（M2-4-1）。
 * 注意：本表无 updated_by 列。
 */
@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 行号，单内唯一 */
    private Integer itemNo;

    private Long materialId;

    /** 来源申请明细行id，支撑按行转订单的多对多追溯（M2-4-1） */
    private Long sourceRequestItemId;

    private BigDecimal quantity;

    private Long unitId;

    /** 含税单价，转订单时取申请行预估单价 */
    private BigDecimal unitPrice;

    /** 税率，如 0.1300 表示 13%，转订单时取默认值 */
    private BigDecimal taxRate;

    /** 含税金额 = 数量 × 单价，服务层计算 */
    private BigDecimal amount;

    /** 不含税金额 = 含税金额 ÷ (1 + 税率)，服务层计算 */
    private BigDecimal noTaxAmount;

    /** 行交货期，默认取主表 */
    private LocalDate deliveryDate;

    private String deliveryPlace;

    /** 已交付数量，仅由入库单确认回写 */
    private BigDecimal deliveredQty;

    private Integer ownerType;

    private Long ownerId;

    private String ownerNo;

    /** 行履约状态：1 待交付、2 部分交付、3 全部交付、4 已关闭 */
    private Integer itemFulfillStatus;

    /** 偏离率，(单价 − 参考价) / 参考价，服务层计算，轮次 6 实现 */
    private BigDecimal deviationRate;

    /** 偏离等级：0 正常、1 关注、2 显著 */
    private Integer deviationLevel;

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
