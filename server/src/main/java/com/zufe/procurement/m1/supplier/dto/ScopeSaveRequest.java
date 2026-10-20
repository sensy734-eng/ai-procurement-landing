package com.zufe.procurement.m1.supplier.dto;

import lombok.Data;

import java.util.List;

/**
 * 供应商供货范围整体保存请求，功能点 M1-2-6。
 * scope_type：1 按物料、2 按物料分类；scope_id 对应两张表的 id。
 */
@Data
public class ScopeSaveRequest {

    private List<Item> scopes;

    @Data
    public static class Item {
        private Integer scopeType;
        private Long scopeId;
        private String remark;
    }
}
