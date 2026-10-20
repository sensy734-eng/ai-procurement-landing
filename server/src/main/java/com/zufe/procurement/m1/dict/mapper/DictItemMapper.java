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
}
