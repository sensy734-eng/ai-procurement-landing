package com.zufe.procurement.m7.assistant.vo;

import lombok.Data;

import java.util.List;

/**
 * 入口提示（M7-1-4）：告诉当前用户本角色能问什么、数据范围到哪。
 * 这是「数据范围感知」的可见落点——用户在提问前就知道自己查不到别的部门。
 */
@Data
public class ProfileVO {

    private String realName;
    private List<String> roleNames;
    /** 是否可问制度与教程 */
    private Boolean knowledgeEnabled;
    /** 是否可问业务数据 */
    private Boolean dataEnabled;
    /** 是否可改参数重跑 */
    private Boolean retryEnabled;
    private ScopeView scope;
    /** 示例问句，按角色给出，避免用户不知从何问起 */
    private List<Sample> samples;

    @Data
    public static class ScopeView {
        private Boolean all;
        private List<String> deptNames;
        private List<String> warehouseNames;
        /** 一句话说明，直接展示在输入框上方 */
        private String hint;
    }

    @Data
    public static class Sample {
        /** 1 知识类、2 教程类、3 数据类 */
        private Integer route;
        private String question;
    }
}
