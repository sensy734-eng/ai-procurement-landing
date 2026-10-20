package com.zufe.procurement.scope;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据范围条件。all 为真表示全量可见，仅系统管理员角色具备。
 * 集合为空且 all 为假时，查询必须失败关闭（1=0），不得退化为全量。
 */
@Data
public class ScopeCondition {

    private boolean all;

    private Set<Long> deptIds = new LinkedHashSet<>();

    private Set<Long> warehouseIds = new LinkedHashSet<>();

    private Set<String> ownerNos = new LinkedHashSet<>();

    public boolean hasDept() {
        return !deptIds.isEmpty();
    }

    public boolean hasWarehouse() {
        return !warehouseIds.isEmpty();
    }

    public boolean hasOwner() {
        return !ownerNos.isEmpty();
    }
}
