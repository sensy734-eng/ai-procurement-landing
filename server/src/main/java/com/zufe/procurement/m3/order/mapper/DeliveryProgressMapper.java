package com.zufe.procurement.m3.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m3.order.entity.DeliveryProgress;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交付进度登记 Mapper。delivery_progress 是过程流水，
 * 不参与履约状态判定（delivered_qty 只由入库单确认回写）。
 */
public interface DeliveryProgressMapper extends BaseMapper<DeliveryProgress> {

    @Select("""
            SELECT * FROM delivery_progress
            WHERE order_id = #{orderId} AND is_deleted = 0
            ORDER BY delivery_date DESC, id DESC
            """)
    List<DeliveryProgress> selectByOrderId(@Param("orderId") Long orderId);

    /** 明细行累计登记数量，交付登记守恒校验用（累计不超订单数量） */
    @Select("""
            SELECT COALESCE(SUM(delivery_qty), 0) FROM delivery_progress
            WHERE order_item_id = #{orderItemId} AND is_deleted = 0
            """)
    BigDecimal sumQtyByOrderItem(@Param("orderItemId") Long orderItemId);

    /** 按订单查交付流水，反审与作废的「未到货」前置判断用 */
    @Select("""
            SELECT COUNT(*) FROM delivery_progress
            WHERE order_id = #{orderId} AND is_deleted = 0
            """)
    long countByOrderId(@Param("orderId") Long orderId);

    /**
     * 批量取每个订单的最后一次交付日期，供供应商交付统计判断是否按期（M9-4-4）。
     * 一次查全，避免在循环里逐单查询。
     */
    @Select("""
            <script>
            SELECT order_id AS orderId, MAX(delivery_date) AS lastDate
            FROM delivery_progress
            WHERE is_deleted = 0 AND order_id IN
            <foreach collection="orderIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY order_id
            </script>
            """)
    List<java.util.Map<String, Object>> lastDeliveryDates(@Param("orderIds") java.util.Collection<Long> orderIds);
}
