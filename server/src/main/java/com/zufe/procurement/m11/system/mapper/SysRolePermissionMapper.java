package com.zufe.procurement.m11.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m11.system.entity.SysRolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限关联 Mapper，功能点 M11-3-2。
 * 授权保存按角色整体重建，先物理删除再插入。
 */
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deletePhysicallyByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT perm_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermIds(@Param("roleId") Long roleId);
}
