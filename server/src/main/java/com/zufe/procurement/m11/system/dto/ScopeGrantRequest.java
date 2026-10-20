package com.zufe.procurement.m11.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 数据范围授权请求，功能点 M11-3-3。
 * 授权主体为用户或角色，维度可组合，整体提交后服务端先删后插。
 */
@Data
public class ScopeGrantRequest {

    /** 1 用户、2 角色 */
    @NotNull(message = "授权主体类型不能为空")
    @Min(value = 1, message = "授权主体类型只能为 1 或 2")
    @Max(value = 2, message = "授权主体类型只能为 1 或 2")
    private Integer subjectType;

    @NotNull(message = "授权主体不能为空")
    private Long subjectId;

    /** 可空，空表示该主体不再持有任何数据范围 */
    private List<ScopeItem> items;

    @Data
    public static class ScopeItem {

        /** 1 仓库、2 部门、3 归属对象 */
        @NotNull(message = "范围维度不能为空")
        @Min(value = 1, message = "范围维度只能为 1 至 3")
        @Max(value = 3, message = "范围维度只能为 1 至 3")
        private Integer scopeDimension;

        @NotNull(message = "范围值不能为空")
        private String scopeValue;
    }
}
