package com.zufe.procurement.scope;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m11.system.entity.SysDataScope;
import com.zufe.procurement.m11.system.mapper.SysDataScopeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 数据范围解析与注入，功能点 M11-3-3 落地点。
 * 页面查询与智能查询共用同一套过滤条件，这是两者结果一致的结构性保证。
 * 注入方式为查询构造器条件，由数据库执行过滤，不在内存中筛选。
 */
@Service
public class DataScopeService {

    /** 1 仓库、2 部门、3 归属对象 */
    private static final int DIM_WAREHOUSE = 1;
    private static final int DIM_DEPT = 2;
    private static final int DIM_OWNER = 3;

    private static final int SUBJECT_USER = 1;
    private static final int SUBJECT_ROLE = 2;

    private final SysDataScopeMapper dataScopeMapper;

    public DataScopeService(SysDataScopeMapper dataScopeMapper) {
        this.dataScopeMapper = dataScopeMapper;
    }

    /**
     * 解析用户的数据范围：用户级与角色级授权取并集，任一角色为全量则全量。
     */
    public ScopeCondition resolve(Long userId, Collection<Long> roleIds, boolean scopeAll) {
        ScopeCondition condition = new ScopeCondition();
        if (scopeAll) {
            condition.setAll(true);
            return condition;
        }
        List<SysDataScope> rows = new ArrayList<>(dataScopeMapper.selectList(
                new QueryWrapper<SysDataScope>()
                        .eq("subject_type", SUBJECT_USER)
                        .eq("subject_id", userId)));
        if (roleIds != null && !roleIds.isEmpty()) {
            rows.addAll(dataScopeMapper.selectList(
                    new QueryWrapper<SysDataScope>()
                            .eq("subject_type", SUBJECT_ROLE)
                            .in("subject_id", roleIds)));
        }
        for (SysDataScope row : rows) {
            if (row.getScopeDimension() == null || row.getScopeValue() == null) {
                continue;
            }
            switch (row.getScopeDimension()) {
                case DIM_WAREHOUSE -> {
                    try {
                        condition.getWarehouseIds().add(Long.valueOf(row.getScopeValue()));
                    } catch (NumberFormatException ignored) {
                        // 脏数据不放大范围，直接忽略
                    }
                }
                case DIM_DEPT -> {
                    try {
                        condition.getDeptIds().add(Long.valueOf(row.getScopeValue()));
                    } catch (NumberFormatException ignored) {
                        // 同上
                    }
                }
                case DIM_OWNER -> condition.getOwnerNos().add(row.getScopeValue());
                default -> {
                    // 未知维度不处理
                }
            }
        }
        return condition;
    }

    /**
     * 部门维度注入。范围为空时失败关闭，返回 1=0。
     */
    public <T> void applyDept(QueryWrapper<T> wrapper, String column, ScopeCondition scope) {
        if (scope.isAll()) {
            return;
        }
        if (!scope.hasDept()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(column, scope.getDeptIds());
    }

    /**
     * 仓库维度注入。范围为空时失败关闭。
     */
    public <T> void applyWarehouse(QueryWrapper<T> wrapper, String column, ScopeCondition scope) {
        if (scope.isAll()) {
            return;
        }
        if (!scope.hasWarehouse()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(column, scope.getWarehouseIds());
    }

    /**
     * 归属对象维度注入。范围为空时不过滤归属，由调用方按业务决定是否叠加其他条件。
     */
    public <T> void applyOwner(QueryWrapper<T> wrapper, String column, ScopeCondition scope) {
        if (scope.isAll() || !scope.hasOwner()) {
            return;
        }
        wrapper.in(column, scope.getOwnerNos());
    }

    public <T> void applyDept(LambdaQueryWrapper<T> wrapper, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column,
                              ScopeCondition scope) {
        if (scope.isAll()) {
            return;
        }
        if (!scope.hasDept()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in(column, scope.getDeptIds());
    }
}
