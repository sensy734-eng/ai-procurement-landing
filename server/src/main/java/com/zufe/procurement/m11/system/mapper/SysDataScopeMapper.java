package com.zufe.procurement.m11.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m11.system.entity.SysDataScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 数据范围授权 Mapper，M11-3-3。
 * 唯一键不含 is_deleted，重建授权一律物理删除。
 */
public interface SysDataScopeMapper extends BaseMapper<SysDataScope> {

    @Delete("DELETE FROM sys_data_scope WHERE subject_type = #{subjectType} AND subject_id = #{subjectId}")
    int deletePhysicallyBySubject(@Param("subjectType") Integer subjectType,
                                  @Param("subjectId") Long subjectId);
}
