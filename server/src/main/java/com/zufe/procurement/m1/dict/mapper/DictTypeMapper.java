package com.zufe.procurement.m1.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.dict.entity.DictType;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典类型 Mapper，功能点 M1-4-1。
 */
public interface DictTypeMapper extends BaseMapper<DictType> {

    /** 连带已删除记录查重：唯一键 uk_dict_type_code 不含 is_deleted */
    @Select("SELECT COUNT(*) FROM dict_type WHERE type_code = #{typeCode}")
    long countByTypeCodeIncludeDeleted(@Param("typeCode") String typeCode);
}
