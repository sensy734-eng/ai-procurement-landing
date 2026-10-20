package com.zufe.procurement.m11.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 权限点按业务域分组展示，功能点 M11-3-1。
 * 分组键为模块编号，组名取 M1 至 M12 的中文名，便于授权页勾选。
 */
@Data
public class PermissionGroupVO {

    private String moduleCode;

    private String moduleName;

    private List<PermissionItemVO> permissions;

    @Data
    public static class PermissionItemVO {
        private Long id;
        private String permCode;
        private String permName;
        private String actionType;
        private String remark;
    }
}
