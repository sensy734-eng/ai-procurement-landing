package com.zufe.procurement.m3.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m3.order.entity.OrderChangeLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单变更历史 Mapper。变更痕迹只增不删，查询按变更时间正序，
 * 与详情页时间线方向一致（早变更在上）。
 */
public interface OrderChangeLogMapper extends BaseMapper<OrderChangeLog> {

    @Select("""
            SELECT * FROM order_change_log
            WHERE order_id = #{orderId} AND is_deleted = 0
            ORDER BY change_at, id
            """)
    List<OrderChangeLog> selectByOrderId(@Param("orderId") Long orderId);
}
