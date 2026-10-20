package com.zufe.procurement.m2.request.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 归属对象选项，功能点 M2-2-5 明细行归属对象指定的下拉数据源。
 * owner_object 档案无独立维护功能点（M1 的 18 个功能点内未列），
 * 此处以只读选项形式暴露，按类型分组返回。
 */
@Data
public class OwnerOptionVO {

    private List<Option> options = new ArrayList<>();

    @Data
    public static class Option {

        private Long id;

        /** 1 公共、2 项目、3 工单 */
        private Integer ownerType;

        private String ownerTypeName;

        private String ownerNo;

        private String ownerName;
    }
}
