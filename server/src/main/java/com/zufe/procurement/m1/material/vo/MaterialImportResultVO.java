package com.zufe.procurement.m1.material.vo;

import lombok.Data;

import java.util.List;

/**
 * 物料导入结果，功能点 M1-1-5。
 * 逐行返回成功失败明细，失败行给出原因，成功行标注新建或更新。
 */
@Data
public class MaterialImportResultVO {

    private int totalCount;
    private int successCount;
    private int failCount;
    private List<Row> rows;

    @Data
    public static class Row {
        /** CSV 数据行号，从 2 起算（第 1 行是表头） */
        private int lineNo;
        private String materialNo;
        private boolean success;
        /** 新建或更新，失败行为空 */
        private String action;
        private String message;
    }
}
