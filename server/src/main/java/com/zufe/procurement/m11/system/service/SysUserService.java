package com.zufe.procurement.m11.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m11.system.entity.SysUser;
import com.zufe.procurement.m11.system.mapper.SysUserMapper;
import com.zufe.procurement.m11.system.vo.UserVO;
import com.zufe.procurement.scope.DataScopeService;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户查询服务，功能点 M11-2-1 用户列表与检索。
 * 部门维度数据范围在此注入，体现"查询必须带范围条件"的约定。
 */
@Service
public class SysUserService {

    private final SysUserMapper userMapper;
    private final DictItemMapper dictItemMapper;
    private final DataScopeService dataScopeService;

    public SysUserService(SysUserMapper userMapper, DictItemMapper dictItemMapper,
                          DataScopeService dataScopeService) {
        this.userMapper = userMapper;
        this.dictItemMapper = dictItemMapper;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<UserVO> page(long pageNo, long pageSize, String keyword, CurrentUser currentUser) {
        Page<SysUser> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 100));
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like("username", trimmed).or().like("real_name", trimmed));
        }
        ScopeCondition scope = currentUser.getScope() == null ? new ScopeCondition() : currentUser.getScope();
        dataScopeService.applyDept(wrapper, "dept_id", scope);
        wrapper.orderByDesc("id");

        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        Map<Long, String> deptNames = loadDeptNames(result.getRecords());
        List<UserVO> records = result.getRecords().stream()
                .map(user -> toVO(user, deptNames.get(user.getDeptId())))
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private Map<Long, String> loadDeptNames(List<SysUser> users) {
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        return dictItemMapper.selectByIds(deptIds).stream()
                .collect(Collectors.toMap(DictItem::getId, DictItem::getItemName, (a, b) -> a));
    }

    private UserVO toVO(SysUser user, String deptName) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(deptName);
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setPwdNeedChange(user.getPwdNeedChange());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
