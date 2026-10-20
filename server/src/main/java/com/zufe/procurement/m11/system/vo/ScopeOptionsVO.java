package com.zufe.procurement.m11.system.vo;

import lombok.Data;

import java.util.List;

/**
 * 数据范围授权可选值，功能点 M11-3-3。
 * 三个维度的候选项由后端给出，前端不硬编码仓库与部门。
 */
@Data
public class ScopeOptionsVO {

    private List<Option> warehouses;

    private List<Option> depts;

    private List<Option> owners;

    @Data
    public static class Option {
        private String value;
        private String label;

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
