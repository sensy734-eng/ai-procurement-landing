package com.zufe.procurement.m5.inbound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 采购入库单，对应 inbound_order，功能点 M5（轮次 7 实现）。
 * 轮次 5 只建最小字段集：订单反审与作废的前置校验需要按订单
 * 判断「是否已产生入库记录」（M3-3-5、M3-1-5）——入库是库存唯一
 * 写入点，有入库记录的订单一律禁止反审与作废，不做级联。
 */
@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inboundNo;

    /** 来源订单id，反审与作废按此计数 */
    private Long orderId;

    /** 入库单状态：1 草稿、2 已确认、3 已作废 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
