package com.zufe.procurement.m5.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m5.inbound.entity.InboundOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购入库单 Mapper。轮次 5 只提供存在性计数：入库是库存唯一写入点，
 * 有入库记录的订单一律禁止反审（M3-3-5）与作废（M3-1-5），
 * 不做级联作废（会破坏库存流水与在途追溯）。
 * 轮次 6 补续号与按验收单查生成的入库单草稿（M4-3-3 多批次）。
 */
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {

    @Select("""
            SELECT COUNT(*) FROM inbound_order
            WHERE order_id = #{orderId} AND is_deleted = 0
            """)
    long countByOrderId(@Param("orderId") Long orderId);

    /** 续号：同日前缀下最大流水（含已删除行，避免单号回收撞历史唯一键） */
    @Select("""
            SELECT MAX(CAST(SUBSTRING(inbound_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED))
            FROM inbound_order
            WHERE inbound_no LIKE CONCAT(#{prefix}, '%')
            """)
    Integer maxSeqByPrefix(@Param("prefix") String prefix);

    @Select("""
            SELECT * FROM inbound_order
            WHERE inspection_id = #{inspectionId} AND is_deleted = 0
            ORDER BY id DESC
            """)
    List<InboundOrder> selectByInspectionId(@Param("inspectionId") Long inspectionId);
}
