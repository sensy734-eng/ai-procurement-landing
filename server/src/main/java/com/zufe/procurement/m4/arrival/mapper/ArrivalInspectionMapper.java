package com.zufe.procurement.m4.arrival.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m4.arrival.entity.ArrivalInspection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 到货验收单 Mapper。轮次 5 只提供存在性计数：订单反审（M3-3-5）
 * 与作废（M3-1-5）要求「未发生到货」，按订单计数判断。
 * 草稿态验收单也计数——只要录了到货事实就不允许反审作废。
 * 轮次 6 补续号、按订单查列表与按订单行累计到货量（到货配额）。
 */
public interface ArrivalInspectionMapper extends BaseMapper<ArrivalInspection> {

    @Select("""
            SELECT COUNT(*) FROM arrival_inspection
            WHERE order_id = #{orderId} AND is_deleted = 0
            """)
    long countByOrderId(@Param("orderId") Long orderId);

    /** 续号：同日前缀下最大流水（含已删除行，避免单号回收撞历史唯一键） */
    @Select("""
            SELECT MAX(CAST(SUBSTRING(inspection_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED))
            FROM arrival_inspection
            WHERE inspection_no LIKE CONCAT(#{prefix}, '%')
            """)
    Integer maxSeqByPrefix(@Param("prefix") String prefix);

    @Select("""
            SELECT * FROM arrival_inspection
            WHERE order_id = #{orderId} AND is_deleted = 0
            ORDER BY id DESC
            """)
    List<ArrivalInspection> selectByOrderId(@Param("orderId") Long orderId);

    @Select("""
            <script>
            SELECT order_item_id AS orderItemId, COALESCE(SUM(arrival_qty), 0) AS arrivalQty
            FROM arrival_inspection_item i
            JOIN arrival_inspection h ON h.id = i.inspection_id
            WHERE h.is_deleted = 0 AND h.order_id = #{orderId}
              AND i.order_item_id IN
            <foreach collection="orderItemIds" item="iid" open="(" separator="," close=")">#{iid}</foreach>
            GROUP BY order_item_id
            </script>
            """)
    List<ArrivalQtyRow> sumArrivalQtyByOrderItems(@Param("orderId") Long orderId,
                                                  @Param("orderItemIds") Collection<Long> orderItemIds);

    /** 按订单行累计到货量的投影行，列别名走驼峰映射 */
    public class ArrivalQtyRow {
        private Long orderItemId;
        private java.math.BigDecimal arrivalQty;

        public Long getOrderItemId() {
            return orderItemId;
        }

        public void setOrderItemId(Long orderItemId) {
            this.orderItemId = orderItemId;
        }

        public java.math.BigDecimal getArrivalQty() {
            return arrivalQty;
        }

        public void setArrivalQty(java.math.BigDecimal arrivalQty) {
            this.arrivalQty = arrivalQty;
        }
    }
}
