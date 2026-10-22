package com.zufe.procurement.m5.stock.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m5.stock.dto.StockAlertQuery;
import com.zufe.procurement.m5.stock.dto.StockQuery;
import com.zufe.procurement.m5.stock.service.StockService;
import com.zufe.procurement.m5.stock.vo.BatchTraceVO;
import com.zufe.procurement.m5.stock.vo.StockAlertVO;
import com.zufe.procurement.m5.stock.vo.StockBalanceVO;
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
 * 库存查询接口。功能点：M5-2-3 批次追溯、M5-3 库存余额与可用量
 * （M5-3-1 至 M5-3-4）、M5-5 库存预警（M5-5-1、M5-5-2）。
 * 全部只读——库存写入只发生在入库单确认/作废（/api/m5/inbounds）。
 */
@RestController
@RequestMapping("/api/m5/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** M5-3-1 库存余额查询：物料/仓库/关键字，可用量随行现算（M5-3-2） */
    @GetMapping("/balance")
    @RequirePermission("m5:stock:list")
    public Result<PageResult<StockBalanceVO>> balance(StockQuery query) {
        return Result.ok(stockService.page(query));
    }

    /** M5-3-2 可用量查询：物料 + 仓库，可用量 = 数量 − 冻结 − 预留（R3） */
    @GetMapping("/available")
    @RequirePermission("m5:stock:available")
    public Result<StockBalanceVO> available(@RequestParam Long materialId, @RequestParam Long warehouseId) {
        return Result.ok(stockService.available(materialId, warehouseId));
    }

    /** M5-3-3 按归属对象分组查询：公共库存与项目/工单库存分开呈现 */
    @GetMapping("/owner-group")
    @RequirePermission("m5:stock:owner_group")
    public Result<PageResult<StockBalanceVO>> ownerGroup(StockQuery query) {
        query.setGroupByOwner(true);
        return Result.ok(stockService.page(query));
    }

    /** M5-3-4 库存余额导出：CSV（UTF-8 BOM），与各模块导出口径一致 */
    @GetMapping("/export")
    @RequirePermission("m5:stock:export")
    public ResponseEntity<byte[]> exportCsv(StockQuery query) {
        byte[] csv = stockService.exportCsv(query);
        String fileName = "库存余额.csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment",
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    /** M5-5-1 库存不足清单：可用量低于物料预警下限的物料与缺口 */
    @GetMapping("/alert")
    @RequirePermission("m5:stock:alert")
    public Result<List<StockAlertVO>> alert(StockAlertQuery query) {
        return Result.ok(stockService.alert(query));
    }

    /** M5-5-2 短缺物料统计：groupBy=warehouse 按仓库、category 按分类汇总缺口 */
    @GetMapping("/alert-stat")
    @RequirePermission("m5:stock:alert")
    public Result<List<StockAlertVO>> alertStat(StockAlertQuery query) {
        return Result.ok(stockService.alertStat(query));
    }

    /** M5-2-3 批次追溯查询：批次号 → 入库明细 → 入库单 → 订单 → 供应商 */
    @GetMapping("/batch-trace")
    @RequirePermission("m5:batch:trace")
    public Result<BatchTraceVO> batchTrace(@RequestParam String batchNo) {
        return Result.ok(stockService.batchTrace(batchNo));
    }
}
