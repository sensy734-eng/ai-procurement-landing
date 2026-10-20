package com.zufe.procurement.scope;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zufe.procurement.m11.system.entity.SysDataScope;
import com.zufe.procurement.m11.system.mapper.SysDataScopeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据范围解析与注入，M11-3-3。
 * 重点验证两条安全性质：范围为空时失败关闭；全量范围不加任何条件。
 */
class DataScopeServiceTest {

    @Test
    @DisplayName("部门范围为空时注入 1=0，不退化为全量")
    void applyDeptFailsClosed() {
        DataScopeService service = new DataScopeService(null);
        QueryWrapper<Object> wrapper = new QueryWrapper<>();

        service.applyDept(wrapper, "dept_id", new ScopeCondition());

        assertThat(wrapper.getSqlSegment()).contains("1 = 0");
    }

    @Test
    @DisplayName("部门范围非空时注入 IN 条件")
    void applyDeptInjectsInCondition() {
        DataScopeService service = new DataScopeService(null);
        ScopeCondition scope = new ScopeCondition();
        scope.getDeptIds().add(1101L);
        scope.getDeptIds().add(1102L);
        QueryWrapper<Object> wrapper = new QueryWrapper<>();

        service.applyDept(wrapper, "dept_id", scope);

        assertThat(wrapper.getSqlSegment()).contains("dept_id IN");
        assertThat(wrapper.getParamNameValuePairs()).containsValues(1101L, 1102L);
    }

    @Test
    @DisplayName("全量范围不加过滤条件")
    void applyDeptSkipsWhenScopeAll() {
        DataScopeService service = new DataScopeService(null);
        ScopeCondition scope = new ScopeCondition();
        scope.setAll(true);
        QueryWrapper<Object> wrapper = new QueryWrapper<>();

        service.applyDept(wrapper, "dept_id", scope);
        service.applyWarehouse(wrapper, "warehouse_id", scope);

        assertThat(wrapper.getSqlSegment()).isEmpty();
    }

    @Test
    @DisplayName("仓库范围为空时注入 1=0")
    void applyWarehouseFailsClosed() {
        DataScopeService service = new DataScopeService(null);
        QueryWrapper<Object> wrapper = new QueryWrapper<>();

        service.applyWarehouse(wrapper, "warehouse_id", new ScopeCondition());

        assertThat(wrapper.getSqlSegment()).contains("1 = 0");
    }

    @Test
    @DisplayName("用户级与角色级授权取并集，按维度归类")
    void resolveMergesUserAndRoleScopes() {
        SysDataScopeMapper mapper = mock(SysDataScopeMapper.class);
        when(mapper.selectList(any()))
                .thenReturn(List.of(scopeRow(2, 1101L), scopeRow(1, 3L), scopeRow(3, "P-2026-001")))
                .thenReturn(List.of(scopeRow(2, 1102L)));
        DataScopeService service = new DataScopeService(mapper);

        ScopeCondition scope = service.resolve(2L, List.of(1L, 2L), false);

        assertThat(scope.isAll()).isFalse();
        assertThat(scope.getDeptIds()).containsExactlyInAnyOrder(1101L, 1102L);
        assertThat(scope.getWarehouseIds()).containsExactly(3L);
        assertThat(scope.getOwnerNos()).containsExactly("P-2026-001");
    }

    @Test
    @DisplayName("全量角色直接返回，不查授权表")
    void resolveReturnsAllWithoutQuery() {
        SysDataScopeMapper mapper = mock(SysDataScopeMapper.class);
        DataScopeService service = new DataScopeService(mapper);

        ScopeCondition scope = service.resolve(1L, List.of(4L), true);

        assertThat(scope.isAll()).isTrue();
        assertThat(scope.getDeptIds()).isEmpty();
        verifyNoInteractions(mapper);
    }

    private SysDataScope scopeRow(int dimension, Object value) {
        SysDataScope row = new SysDataScope();
        row.setScopeDimension(dimension);
        row.setScopeValue(String.valueOf(value));
        return row;
    }
}
