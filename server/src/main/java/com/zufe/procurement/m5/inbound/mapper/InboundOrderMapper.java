package com.zufe.procurement.m5.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m5.inbound.entity.InboundOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购入库单 Mapper。轮次 5 只提供存在性计数：入库是库存唯一写入点，
 * 有入库记录的订单一律禁止反审（M3-3-5）与作废（M3-1-5），
 * 不做级联作废（会破坏库存流水与在途追溯）。
 */
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {

    @Select("""
            SELECT COUNT(*) FROM inbound_order
            WHERE order_id = #{orderId} AND is_deleted = 0
            """)
    long countByOrderId(@Param("orderId") Long orderId);
}
