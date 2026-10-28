package com.zufe.procurement.m12.dashboard.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.m12.dashboard.dto.ChartQuery;
import com.zufe.procurement.m12.dashboard.dto.SummaryQuery;
import com.zufe.procurement.m12.dashboard.service.DashboardService;
import com.zufe.procurement.m12.dashboard.vo.OverviewVO;
import com.zufe.procurement.m12.dashboard.vo.PurchaseSummaryRow;
import com.zufe.procurement.m12.dashboard.vo.StatusChartVO;
import com.zufe.procurement.m12.dashboard.vo.StockAlertChartVO;
import com.zufe.procurement.m12.dashboard.vo.StockSummaryRow;
import com.zufe.procurement.m12.dashboard.vo.SupplierRankVO;
import com.zufe.procurement.m12.dashboard.vo.TrendChartVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 管理后台与数据可视化接口（M12，8 个端点）。
 *
 * M12-1 后台首页只做导航聚合，不额外设权限点：入口本身按当前用户的权限点过滤，
 * 有权限才返回——用登录态校验就够，强行绑一个权限点会让「首页谁能看」变成配置问题。
 * 其余七个端点各自绑定 M12 的动作级权限点。
 * 全部只读，与 M12 的「只读」状态变化一致。
 */
@RestController
@RequestMapping("/api/m12")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** M12-1-1 / M12-1-2 后台首页：五类维护入口聚合 + 指标卡，入口按权限过滤 */
    @GetMapping("/overview")
    public Result<OverviewVO> overview() {
        return Result.ok(dashboardService.overview());
    }

    /** M12-2-1 采购单据汇总：groupBy=status/supplier/material/month */
    @GetMapping("/summary/purchase")
    @RequirePermission("m12:summary:purchase")
    public Result<List<PurchaseSummaryRow>> purchaseSummary(SummaryQuery query) {
        return Result.ok(dashboardService.purchaseSummary(query));
    }

    /** M12-2-2 库存汇总：groupBy=warehouse/category/owner */
    @GetMapping("/summary/stock")
    @RequirePermission("m12:summary:stock")
    public Result<List<StockSummaryRow>> stockSummary(SummaryQuery query) {
        return Result.ok(dashboardService.stockSummary(query));
    }

    /** M12-2-3 汇总结果导出：type=purchase 采购单据，type=stock 库存；CSV（UTF-8 BOM） */
    @GetMapping("/summary/export")
    @RequirePermission("m12:summary:export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "purchase") String type,
                                         SummaryQuery query) {
        byte[] csv = dashboardService.export(type, query);
        String fileName = "stock".equalsIgnoreCase(type) ? "库存汇总.csv" : "采购单据汇总.csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /** M12-3-1 采购订单状态分布（五个业务桶 + 不计入的草稿与作废） */
    @GetMapping("/charts/status")
    @RequirePermission("m12:chart:status")
    public Result<StatusChartVO> statusChart(ChartQuery query) {
        return Result.ok(dashboardService.statusChart(query));
    }

    /** M12-3-2 采购金额月度趋势（近 12 个月，缺月补零） */
    @GetMapping("/charts/trend")
    @RequirePermission("m12:chart:trend")
    public Result<TrendChartVO> trendChart(ChartQuery query) {
        return Result.ok(dashboardService.trendChart(query));
    }

    /** M12-3-3 供应商采购金额排名（Top-N，占比分母是区间总金额） */
    @GetMapping("/charts/supplier")
    @RequirePermission("m12:chart:supplier")
    public Result<SupplierRankVO> supplierRank(ChartQuery query) {
        return Result.ok(dashboardService.supplierRank(query));
    }

    /** M12-3-4 库存不足物料统计（口径与 M5-5-1 库存预警一致） */
    @GetMapping("/charts/stock-alert")
    @RequirePermission("m12:chart:stockalert")
    public Result<StockAlertChartVO> stockAlertChart(ChartQuery query) {
        return Result.ok(dashboardService.stockAlertChart(query));
    }
}
