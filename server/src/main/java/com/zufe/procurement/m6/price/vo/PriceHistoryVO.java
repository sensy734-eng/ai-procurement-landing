package com.zufe.procurement.m6.price.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 区间成交口径（M9-4-5）。
 *
 * 与快照口径的区别：快照按「近 N 笔 / 近 N 天」的固定窗口统计，供页面展示；
 * 本对象按用户指定的起止日期实时汇总成交明细，供智能查询回答「近半年均价」这类问句。
 * 两者共用同一条成交定义（lifecycle 3/4 且 audit_status=2），只是统计范围不同。
 */
@Data
public class PriceHistoryVO {

    private Long materialId;
    private String materialNo;
    private String materialName;
    private Long supplierId;
    private String supplierName;

    /** 金额加权均价 = Σ金额 / Σ数量，无样本为 null（不伪造） */
    private BigDecimal avgPrice;
    private BigDecimal totalAmount;
    private BigDecimal totalQty;
    /** 成交明细行数（一条订单行算一笔） */
    private Integer sampleCount;
    private BigDecimal minUnitPrice;
    private BigDecimal maxUnitPrice;
    private String dateFrom;
    private String dateTo;
    private List<DealRow> deals = new ArrayList<>();

    @Data
    public static class DealRow {
        private String orderNo;
        private String orderDate;
        private String supplierName;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        private BigDecimal amount;
    }
}
