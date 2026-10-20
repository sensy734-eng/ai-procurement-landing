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

    /**
     * 账号查重。uk_username 不含 is_deleted，逻辑删除的账号仍占用唯一键，
     * 因此新增时必须连已删除的一起查，否则插入会撞唯一键报数据库错误。
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    int countByUsernameIncludeDeleted(@Param("username") String username);

    /**
     * 统计某角色下处于启用状态的用户数，停用角色或删除角色前的保护性校验。
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            WHERE ur.role_id = #{roleId} AND u.is_deleted = 0 AND u.status = 1
            """)
    int countActiveUsersByRole(@Param("roleId") Long roleId);

    /**
     * 某角色下处于启用状态的用户id，按用户id升序。
     * 审批任务生成时按角色解析审批人（M11-5-4），与认证链路的角色解析口径一致：
     * 用户启用、角色启用、关联未删除。
     */
    @Select("""
            SELECT u.id FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            WHERE ur.role_id = #{roleId} AND ur.is_deleted = 0
              AND u.is_deleted = 0 AND u.status = 1
            ORDER BY u.id
            """)
    List<Long> selectActiveUserIdsByRole(@Param("roleId") Long roleId);
}
