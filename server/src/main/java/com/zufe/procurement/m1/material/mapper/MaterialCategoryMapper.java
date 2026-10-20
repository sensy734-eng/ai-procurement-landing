package com.zufe.procurement.m1.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.material.entity.MaterialCategory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 物料分类 Mapper，功能点 M1-1-7。
 */
public interface MaterialCategoryMapper extends BaseMapper<MaterialCategory> {

    @Select("SELECT COUNT(*) FROM material_category WHERE category_no = #{categoryNo}")
    long countByCategoryNoIncludeDeleted(@Param("categoryNo") String categoryNo);

    /** 全量分类，含停用，供前端构建分类树 */
    @Select("SELECT * FROM material_category ORDER BY sort_no, id")
    List<MaterialCategory> selectAllForTree();
}
