package com.zufe.procurement.m12.dashboard.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台首页聚合（M12-1-1 功能导航 / M12-1-2 维护入口聚合）。
 *
 * 两件事：
 * 1. 五类维护入口（基础数据、字典、用户角色、知识库、审计）按当前用户权限点过滤后返回，
 *    无权限的入口连标题都不出现——前端不再自行判断，避免"菜单没有、首页有"的口径分裂；
 * 2. 每类入口带一个计数，计数走同一张表的 count，用于首页指标卡的直观呈现。
 */
@Data
public class OverviewVO {

    private List<EntryGroup> groups = new ArrayList<>();

    /** 首页指标卡 */
    private List<Metric> metrics = new ArrayList<>();

    /** 数据范围提示（与登录返回同源，前端仅展示） */
    private String scopeHint;

    private String realName;

    private List<String> roleNames = new ArrayList<>();

    @Data
    public static class EntryGroup {

        /** 分组编码：base / dict / platform / kb / audit */
        private String key;

        private String title;

        /** 分组图标名，前端映射到 Element Plus 图标 */
        private String icon;

        private List<Entry> entries = new ArrayList<>();
    }

    @Data
    public static class Entry {

        private String title;

        private String path;

        /** 进入该入口所需的权限点，前端仅作展示，实际由后端接口再校验 */
        private String perm;

        /** 计数含义，如「个物料」「条日志」 */
        private String countLabel;

        /** 计数；无计数语义时为 null */
        private Long count;

        private String description;
    }

    @Data
    public static class Metric {

        private String key;

        private String label;

        private String value;

        private String unit;

        private String hint;
    }
}
