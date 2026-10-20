package com.zufe.procurement.m3.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购订单明细行 Mapper。uk_order_item_no（order_id, item_no）不含 is_deleted，
 * 行号按单内最大行号 + 1 分配。
 */
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItem> {

    @Select("""
            SELECT COALESCE(MAX(item_no), 0) FROM purchase_order_item
            WHERE order_id = #{orderId} AND is_deleted = 0
            """)
    Integer maxItemNo(@Param("orderId") Long orderId);

    @Select("""
            SELECT * FROM purchase_order_item
            WHERE order_id = #{orderId} AND is_deleted = 0
            ORDER BY item_no
            """)
    List<PurchaseOrderItem> selectByOrderId(@Param("orderId") Long orderId);
}
