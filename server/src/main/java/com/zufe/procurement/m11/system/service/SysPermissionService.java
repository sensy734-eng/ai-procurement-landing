package com.zufe.procurement.m11.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m11.system.entity.SysPermission;
import com.zufe.procurement.m11.system.mapper.SysPermissionMapper;
import com.zufe.procurement.m11.system.vo.PermissionGroupVO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限点查询服务，功能点 M11-3-1 权限点列表。
 * 按模块编号分组展示，授权页以模块为一组勾选，不自造第二套分类。
 */
@Service
public class SysPermissionService {

    /** 模块编号与中文名，与 M1 至 M12 的编号体系统一 */
    private static final Map<String, String> MODULE_NAMES = new LinkedHashMap<>();

    static {
        MODULE_NAMES.put("M1", "基础数据管理");
        MODULE_NAMES.put("M2", "采购申请管理");
        MODULE_NAMES.put("M3", "采购订单管理");
        MODULE_NAMES.put("M4", "到货验收管理");
        MODULE_NAMES.put("M5", "采购入库与库存管理");
        MODULE_NAMES.put("M6", "价格参考与采购审批辅助");
        MODULE_NAMES.put("M7", "智能助手统一入口与问题路由");
        MODULE_NAMES.put("M8", "企业制度与ERP操作教程问答");
        MODULE_NAMES.put("M9", "受控自然语言业务数据查询");
        MODULE_NAMES.put("M10", "知识库管理");
        MODULE_NAMES.put("M11", "平台与权限管理");
        MODULE_NAMES.put("M12", "管理后台与数据可视化");
    }

    private final SysPermissionMapper permissionMapper;

    public SysPermissionService(SysPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    public List<PermissionGroupVO> grouped() {
        List<SysPermission> all = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getModuleCode)
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));
        Map<String, List<SysPermission>> byModule = all.stream()
                .filter(p -> p.getModuleCode() != null)
                .collect(Collectors.groupingBy(SysPermission::getModuleCode,
                        LinkedHashMap::new, Collectors.toList()));
        return byModule.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> moduleOrder(e.getKey())))
                .map(entry -> {
                    PermissionGroupVO group = new PermissionGroupVO();
                    group.setModuleCode(entry.getKey());
                    group.setModuleName(MODULE_NAMES.getOrDefault(entry.getKey(), entry.getKey()));
                    group.setPermissions(entry.getValue().stream().map(p -> {
                        PermissionGroupVO.PermissionItemVO item = new PermissionGroupVO.PermissionItemVO();
                        item.setId(p.getId());
                        item.setPermCode(p.getPermCode());
                        item.setPermName(p.getPermName());
                        item.setActionType(p.getActionType());
                        item.setRemark(p.getRemark());
                        return item;
                    }).collect(Collectors.toList()));
                    return group;
                }).collect(Collectors.toList());
    }

    private int moduleOrder(String moduleCode) {
        int index = 0;
        for (String code : MODULE_NAMES.keySet()) {
            if (code.equals(moduleCode)) {
                return index;
            }
            index++;
        }
        return Integer.MAX_VALUE;
    }
}
