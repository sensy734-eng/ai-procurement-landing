package com.zufe.procurement.m1.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.material.entity.Material;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 物料 Mapper，功能点 M1-1-1 至 M1-1-5。
 */
public interface MaterialMapper extends BaseMapper<Material> {

    /**
     * 按编码计数，连带已逻辑删除的记录一起查：
     * 唯一键 uk_material_no 不含 is_deleted，删除后的编码不允许复用。
     */
    @Select("SELECT COUNT(*) FROM material WHERE material_no = #{materialNo}")
    long countByMaterialNoIncludeDeleted(@Param("materialNo") String materialNo);
}
