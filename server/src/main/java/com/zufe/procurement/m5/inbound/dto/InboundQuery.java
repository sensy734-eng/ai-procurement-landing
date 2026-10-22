package com.zufe.procurement.m5.inbound.dto;

import lombok.Data;

/**
 * 入库单列表查询（M5-1-2）。单号/仓库/日期筛选；
 * 数据范围在 Service 层按仓库维度注入（有仓库范围的用户才过滤，
 * 部门维度的采购员/主管与 admin 不过滤——库存与入库单不是部门维度实体，
 * 清单 M5-1-2 明确授权这两类角色查询）。
 */
@Data
public class InboundQuery {

    private long pageNo = 1;

    private long pageSize = 10;

    private String keyword;

    private Long warehouseId;

    /** 入库日期起（含） */
    private String dateFrom;

    /** 入库日期止（含） */
    private String dateTo;

    /** 入库单状态：1 草稿、2 已确认、3 已作废，空=全部 */
    private Integer status;

    /** 来源订单id（按订单反查入库单） */
    private Long orderId;
}
