package com.zufe.procurement.m3.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购订单 Mapper。轮次 4 仅承担申请转订单的草稿生成（M2-4），
 * 完整订单维护在轮次 5 实现。uk_order_no 不含 is_deleted。
 */
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    @Select("""
            SELECT MAX(CAST(SUBSTRING(order_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED))
            FROM purchase_order
            WHERE order_no LIKE CONCAT(#{prefix}, '%')
            """)
    Integer maxSeqByPrefix(@Param("prefix") String prefix);
}
