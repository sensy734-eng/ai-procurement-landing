package com.zufe.procurement.m5.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m5.inbound.entity.InboundItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 采购入库明细 Mapper。轮次 6 提供按订单行累计「已生成入库量」：
 * 草稿也占入库配额，否则同一验收单可被反复生成草稿超发（M4-3-4）。
 * 作废单不计入——作废即释放配额。
 */
public interface InboundItemMapper extends BaseMapper<InboundItem> {

    @Select("""
            SELECT * FROM inbound_item
            WHERE inbound_id = #{inboundId} AND is_deleted = 0
            ORDER BY id
            """)
    List<InboundItem> selectByInboundId(@Param("inboundId") Long inboundId);

    /** 批量按入库单取明细（列表页展开行用） */
    @Select("""
            <script>
            SELECT * FROM inbound_item
            WHERE is_deleted = 0 AND inbound_id IN
            <foreach collection="inboundIds" item="iid" open="(" separator="," close=")">#{iid}</foreach>
            ORDER BY inbound_id, id
            </script>
            """)
    List<InboundItem> selectByInboundIds(@Param("inboundIds") Collection<Long> inboundIds);

    /** 入库单明细合计数量 */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0) FROM inbound_item
            WHERE inbound_id = #{inboundId} AND is_deleted = 0
            """)
    java.math.BigDecimal sumQtyByInboundId(@Param("inboundId") Long inboundId);

    /**
     * 批次号续号（M5-2-1 规则生成）：同日前缀下最大流水，含逻辑删除行
     * ——批次号是仓储事实标识，回收会撞历史批次。
     */
    @Select("""
            SELECT MAX(CAST(SUBSTRING(batch_no, #{prefixLength} + 1) AS UNSIGNED))
            FROM inbound_item
            WHERE batch_no LIKE CONCAT(#{prefix}, '%')
            """)
    Integer maxBatchSeqByPrefix(@Param("prefix") String prefix, @Param("prefixLength") int prefixLength);

    @Select("""
            <script>
            SELECT i.order_item_id AS orderItemId, COALESCE(SUM(i.quantity), 0) AS generatedQty
            FROM inbound_item i
            JOIN inbound_order h ON h.id = i.inbound_id
            WHERE h.is_deleted = 0 AND h.status IN (1, 2) AND i.is_deleted = 0
              AND i.order_item_id IN
            <foreach collection="orderItemIds" item="iid" open="(" separator="," close=")">#{iid}</foreach>
            GROUP BY i.order_item_id
            </script>
            """)
    List<GeneratedQtyRow> sumGeneratedQtyByOrderItems(@Param("orderItemIds") Collection<Long> orderItemIds);

    /** 按订单行累计已生成入库量的投影行 */
    public class GeneratedQtyRow {
        private Long orderItemId;
        private java.math.BigDecimal generatedQty;

        public Long getOrderItemId() {
            return orderItemId;
        }

        public void setOrderItemId(Long orderItemId) {
            this.orderItemId = orderItemId;
        }

        public java.math.BigDecimal getGeneratedQty() {
            return generatedQty;
        }

        public void setGeneratedQty(java.math.BigDecimal generatedQty) {
            this.generatedQty = generatedQty;
        }
    }
}
