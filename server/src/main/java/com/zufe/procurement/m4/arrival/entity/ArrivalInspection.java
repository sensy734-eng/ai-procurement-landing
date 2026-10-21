package com.zufe.procurement.m4.arrival.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 到货验收单，对应 arrival_inspection，功能点 M4（轮次 6 实现）。
 * 轮次 5 只建最小字段集：订单反审与作废的前置校验需要按订单
 * 判断「是否已产生到货记录」（M3-3-5、M3-1-5），完整维护在轮次 6 补。
 */
@Data
@TableName("arrival_inspection")
public class ArrivalInspection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inspectionNo;

    /** 来源订单id，反审与作废按此计数 */
    private Long orderId;

    /** 验收单状态：1 草稿、2 已验收 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
