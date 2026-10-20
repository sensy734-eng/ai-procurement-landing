package com.zufe.procurement.m11.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m11.system.entity.SysRole;
import com.zufe.procurement.m11.system.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper，附带角色与权限点的关联查询，供认证与菜单渲染使用。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT r.id FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND ur.is_deleted = 0
              AND r.is_deleted = 0 AND r.status = 1
            """)
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Select("""
            SELECT r.role_code FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND ur.is_deleted = 0
              AND r.is_deleted = 0 AND r.status = 1
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
            SELECT r.* FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND ur.is_deleted = 0
              AND r.is_deleted = 0 AND r.status = 1
            """)
    List<SysRole> selectRoles(@Param("userId") Long userId);

    /**
     * 用户持有的动作级权限点，多角色取并集。
     */
    @Select("""
            SELECT DISTINCT p.perm_code FROM sys_permission p
            JOIN sys_role_permission rp ON rp.perm_id = p.id
            JOIN sys_role r ON r.id = rp.role_id
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND ur.is_deleted = 0 AND rp.is_deleted = 0
              AND p.is_deleted = 0 AND r.is_deleted = 0 AND r.status = 1
            """)
    List<String> selectPermCodes(@Param("userId") Long userId);
}
