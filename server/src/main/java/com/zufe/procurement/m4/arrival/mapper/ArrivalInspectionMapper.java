package com.zufe.procurement.m4.arrival.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m4.arrival.entity.ArrivalInspection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 到货验收单 Mapper。轮次 5 只提供存在性计数：订单反审（M3-3-5）
 * 与作废（M3-1-5）要求「未发生到货」，按订单计数判断。
 * 草稿态验收单也计数——只要录了到货事实就不允许反审作废。
 */
public interface ArrivalInspectionMapper extends BaseMapper<ArrivalInspection> {

    @Select("""
            SELECT COUNT(*) FROM arrival_inspection
            WHERE order_id = #{orderId} AND is_deleted = 0
            """)
    long countByOrderId(@Param("orderId") Long orderId);
}
