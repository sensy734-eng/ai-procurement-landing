package com.zufe.procurement.m7.assistant.service;

import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m7.assistant.vo.ProfileVO;
import com.zufe.procurement.scope.ScopeCondition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入口提示（M7-1-4）：先告诉用户「你能问什么、能看到哪些部门/仓库」，再让他提问。
 *
 * 数据范围在提示里显式写出来，避免用户问完之后才因为 NoPermission 才知道自己看不到，
 * 将当前数据范围反馈给界面，便于用户核对查询边界。
 */
@Service
public class AiProfileService {

    private final DictItemMapper dictItemMapper;
    private final WarehouseMapper warehouseMapper;

    public AiProfileService(DictItemMapper dictItemMapper, WarehouseMapper warehouseMapper) {
        this.dictItemMapper = dictItemMapper;
        this.warehouseMapper = warehouseMapper;
    }

    public ProfileVO profile() {
        CurrentUser user = AuthContext.getOrThrow();
        ProfileVO vo = new ProfileVO();
        vo.setRealName(user.getRealName());
        vo.setRoleNames(new ArrayList<>(user.getRoleNames()));
        vo.setKnowledgeEnabled(user.hasPermission("m8:kb:query"));
        vo.setDataEnabled(user.hasPermission("m9:data:query"));
        vo.setRetryEnabled(user.hasPermission("m7:query:retry"));
        vo.setScope(scopeView(user.getScope()));
        vo.setSamples(samples(vo.getKnowledgeEnabled(), vo.getDataEnabled()));
        return vo;
    }

    private ProfileVO.ScopeView scopeView(ScopeCondition scope) {
        ProfileVO.ScopeView view = new ProfileVO.ScopeView();
        boolean all = scope == null || scope.isAll();
        view.setAll(all);
        List<String> deptNames = new ArrayList<>();
        List<String> warehouseNames = new ArrayList<>();
        if (scope != null && !all) {
            if (scope.hasDept()) {
                Map<Long, String> deptById = new LinkedHashMap<>();
                for (DictItem item : dictItemMapper.selectByTypeCode("dept")) {
                    deptById.put(item.getId(), item.getItemName());
                }
                for (Long id : scope.getDeptIds()) {
                    deptNames.add(deptById.getOrDefault(id, "部门 " + id));
                }
            }
            if (scope.hasWarehouse()) {
                for (Warehouse warehouse : warehouseMapper.selectBatchIds(scope.getWarehouseIds())) {
                    warehouseNames.add(warehouse.getWarehouseName());
                }
            }
        }
        view.setDeptNames(deptNames);
        view.setWarehouseNames(warehouseNames);
        if (all) {
            view.setHint("您可以看到全部部门与仓库的数据。");
        } else if (!deptNames.isEmpty()) {
            view.setHint("您的数据范围：" + String.join("、", deptNames) + "。其他部门的单据查不到。");
        } else if (!warehouseNames.isEmpty()) {
            view.setHint("您的数据范围：" + String.join("、", warehouseNames) + "。其他仓库的库存查不到。");
        } else {
            view.setHint("您暂无部门或仓库维度的数据范围，业务数据查询会返回空结果。");
        }
        return view;
    }

    private List<ProfileVO.Sample> samples(Boolean knowledge, Boolean data) {
        List<ProfileVO.Sample> samples = new ArrayList<>();
        if (Boolean.TRUE.equals(knowledge)) {
            samples.add(sample(1, "采购申请提交后多久内要审批完"));
            samples.add(sample(2, "怎么新增采购申请"));
        }
        if (Boolean.TRUE.equals(data)) {
            samples.add(sample(3, "CG-DD-20261011-0001 现在什么状态"));
            samples.add(sample(3, "内六角螺栓还有多少库存"));
            samples.add(sample(3, "哪些订单还没交货"));
        }
        return samples;
    }

    private ProfileVO.Sample sample(Integer route, String question) {
        ProfileVO.Sample sample = new ProfileVO.Sample();
        sample.setRoute(route);
        sample.setQuestion(question);
        return sample;
    }
}
