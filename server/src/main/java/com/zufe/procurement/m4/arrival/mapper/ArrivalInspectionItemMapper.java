package com.zufe.procurement.m4.arrival.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m4.arrival.entity.ArrivalInspectionItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 到货验收明细行 Mapper。
 * uk_aii_inspection_item（inspection_id, order_item_id）不含 is_deleted——
 * 同一次登记里同一订单行只能有一行，更新行时按 (inspection_id, order_item_id)
 * 定位而非按 id，与订单行号同口径处理。
 */
public interface ArrivalInspectionItemMapper extends BaseMapper<ArrivalInspectionItem> {

    @Select("""
            SELECT * FROM arrival_inspection_item
            WHERE inspection_id = #{inspectionId} AND is_deleted = 0
            ORDER BY id
            """)
    List<ArrivalInspectionItem> selectByInspectionId(@Param("inspectionId") Long inspectionId);

    /** 草稿整单删除：子表物理删除后重建，避免唯一键残留 */
    @Delete("""
            DELETE FROM arrival_inspection_item WHERE inspection_id = #{inspectionId}
            """)
    int deleteByInspectionId(@Param("inspectionId") Long inspectionId);
}
