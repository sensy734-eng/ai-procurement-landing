package com.zufe.procurement.m1.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.warehouse.entity.WarehouseLocation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库位 Mapper，功能点 M1-3-2。
 */
public interface WarehouseLocationMapper extends BaseMapper<WarehouseLocation> {

    /** 库位编码在仓库内唯一，连带已删除记录一起查 */
    @Select("SELECT COUNT(*) FROM warehouse_location WHERE warehouse_id = #{warehouseId} AND location_no = #{locationNo}")
    long countByWarehouseAndNoIncludeDeleted(@Param("warehouseId") Long warehouseId,
                                             @Param("locationNo") String locationNo);

    @Select("SELECT * FROM warehouse_location WHERE warehouse_id = #{warehouseId} AND is_deleted = 0 ORDER BY id")
    List<WarehouseLocation> selectByWarehouseId(@Param("warehouseId") Long warehouseId);
}
