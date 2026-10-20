package com.zufe.procurement.m1.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.warehouse.entity.WarehouseUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 仓库使用人关系 Mapper，功能点 M1-3-3。
 * 唯一键 uk_warehouse_user 不含 is_deleted，分配时物理删除后重建。
 */
public interface WarehouseUserMapper extends BaseMapper<WarehouseUser> {

    @Select("SELECT * FROM warehouse_user WHERE warehouse_id = #{warehouseId} AND is_deleted = 0 ORDER BY is_default DESC, id")
    List<WarehouseUser> selectByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Select("SELECT user_id FROM warehouse_user WHERE warehouse_id = #{warehouseId} AND is_deleted = 0")
    List<Long> selectUserIdsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Delete("DELETE FROM warehouse_user WHERE warehouse_id = #{warehouseId}")
    int physicalDeleteByWarehouseId(@Param("warehouseId") Long warehouseId);
}
