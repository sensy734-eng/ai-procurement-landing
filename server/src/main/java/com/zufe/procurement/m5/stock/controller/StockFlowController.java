package com.zufe.procurement.m5.stock.controller;

import com.zufe.procurement.common.PageResult;
import com.zufe.procurement.common.Result;
import com.zufe.procurement.m5.inbound.service.InboundOrderService;
import com.zufe.procurement.m5.inbound.vo.InboundVO;
import com.zufe.procurement.m5.stock.dto.FlowQuery;
import com.zufe.procurement.m5.stock.service.StockService;
import com.zufe.procurement.m5.stock.vo.FlowVO;
import com.zufe.procurement.m5.stock.vo.ReconcileVO;
import com.zufe.procurement.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存流水接口。功能点：M5-4-2 流水查询、M5-4-3 来源单据回溯、
 * M5-4-4 余额与流水对账（归系统管理员）。流水只由入库单确认/
 * 作废写入（M5-4-1），本控制器全部只读。
 */
@RestController
@RequestMapping("/api/m5/flows")
public class StockFlowController {

    private final StockService stockService;

    private final InboundOrderService inboundOrderService;

    public StockFlowController(StockService stockService, InboundOrderService inboundOrderService) {
        this.stockService = stockService;
        this.inboundOrderService = inboundOrderService;
    }

    /** M5-4-2 流水查询：物料/仓库/时间/单据类型/批次筛选，按数据范围过滤 */
    @GetMapping("/page")
    @RequirePermission("m5:flow:list")
    public Result<PageResult<FlowVO>> page(FlowQuery query) {
        return Result.ok(stockService.flowPage(query));
    }

    /** M5-4-3 来源单据回溯：流水分录 → 对应入库单详情 */
    @GetMapping("/{id}/source")
    @RequirePermission("m5:flow:trace")
    public Result<InboundVO> source(@PathVariable Long id) {
        return Result.ok(inboundOrderService.flowSource(id));
    }

    /**
     * M5-4-4 余额与流水对账：指定时间点前流水累计 vs 当前余额，
     * 逐键（物料+仓库+批次+归属）比对，双端差集都算差异。
     */
    @GetMapping("/reconcile")
    @RequirePermission("m5:flow:reconcile")
    public Result<ReconcileVO> reconcile(@RequestParam(required = false) String asOf,
                                         @RequestParam(required = false) Long warehouseId) {
        return Result.ok(stockService.reconcile(asOf, warehouseId));
    }
}
