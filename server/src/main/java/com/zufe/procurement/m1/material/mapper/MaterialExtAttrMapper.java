package com.zufe.procurement.m1.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.material.entity.MaterialExtAttr;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 物料扩展属性 Mapper，功能点 M1-1-6。
 * 唯一键 uk_ext_material_attr 不含 is_deleted，
 * 因此保存用物理删除后重建，不用逻辑删除。
 */
public interface MaterialExtAttrMapper extends BaseMapper<MaterialExtAttr> {

    @Select("SELECT * FROM material_ext_attr WHERE material_id = #{materialId} AND is_deleted = 0 ORDER BY sort_no, id")
    List<MaterialExtAttr> selectByMaterialId(@Param("materialId") Long materialId);

    @Delete("DELETE FROM material_ext_attr WHERE material_id = #{materialId}")
    int physicalDeleteByMaterialId(@Param("materialId") Long materialId);
}
