package com.zufe.procurement.m1.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.dict.entity.DictItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 字典项 Mapper，M1-4-1。
 */
public interface DictItemMapper extends BaseMapper<DictItem> {

    @Select("""
            SELECT i.* FROM dict_item i
            JOIN dict_type t ON t.id = i.type_id
            WHERE t.type_code = #{typeCode} AND t.status = 1 AND t.is_deleted = 0
              AND i.status = 1 AND i.is_deleted = 0
            ORDER BY i.sort_no, i.id
            """)
    List<DictItem> selectByTypeCode(@Param("typeCode") String typeCode);

    /**
     * 判断某 id 是否为指定字典类型下的有效项，用于数据范围授权时的部门校验。
     */
    @Select("""
            SELECT COUNT(*) FROM dict_item i
            JOIN dict_type t ON t.id = i.type_id
            WHERE t.type_code = #{typeCode} AND i.id = #{id} AND i.is_deleted = 0
            """)
    int countByTypeCodeAndId(@Param("typeCode") String typeCode, @Param("id") Long id);

    /**
     * 按类型编码与项编码计数，用于 supplier_type、material_type 一类
     * “业务字段存字典项编码而非 id”的场景校验。
     */
    @Select("""
            SELECT COUNT(*) FROM dict_item i
            JOIN dict_type t ON t.id = i.type_id
            WHERE t.type_code = #{typeCode} AND i.item_code = #{itemCode} AND i.is_deleted = 0
            """)
    int countByTypeCodeAndItemCode(@Param("typeCode") String typeCode, @Param("itemCode") String itemCode);

    /**
     * 管理端全量字典项，含停用项，供 M1-4-1 维护页面使用。
     */
    @Select("""
            SELECT i.* FROM dict_item i
            JOIN dict_type t ON t.id = i.type_id
            WHERE t.type_code = #{typeCode} AND t.is_deleted = 0
            ORDER BY i.sort_no, i.id
            """)
    List<DictItem> selectAllByTypeCode(@Param("typeCode") String typeCode);

    /** 项编码在同类型内唯一，连带已删除记录一起查 */
    @Select("SELECT COUNT(*) FROM dict_item WHERE type_id = #{typeId} AND item_code = #{itemCode}")
    long countByTypeIdAndItemCodeIncludeDeleted(@Param("typeId") Long typeId, @Param("itemCode") String itemCode);

    @Select("SELECT COUNT(*) FROM dict_item WHERE type_id = #{typeId} AND is_deleted = 0")
    long countByTypeId(@Param("typeId") Long typeId);
}
