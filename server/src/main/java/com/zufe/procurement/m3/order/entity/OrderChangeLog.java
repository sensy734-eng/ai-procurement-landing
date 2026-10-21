package com.zufe.procurement.m3.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单变更历史，对应 order_change_log，功能点 M3-4-1 变更登记、M3-4-2 变更历史查看。
 * 只增不删：变更痕迹是审计依据，不提供修改与删除入口。
 * 注意：本表无 updated_by 列（与 purchase_order_item 同）。
 */
@Data
@TableName("order_change_log")
public class OrderChangeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 订单号，冗余，订单删除后仍可读 */
    private String orderNo;

    /** 订单明细行id，整单变更时为空 */
    private Long orderItemId;

    /** 行号，冗余 */
    private Integer itemNo;

    /** 变更字段：quantity、unit_price、delivery_date、delivery_place、settlement_type */
    private String changeField;

    private String beforeValue;

    private String afterValue;

    /** 变更原因，必填，保证痕迹可读 */
    private String changeReason;

    private Long operatorId;

    private LocalDateTime changeAt;

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
