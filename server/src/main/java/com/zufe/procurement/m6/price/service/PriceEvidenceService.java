package com.zufe.procurement.m6.price.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m3.order.entity.PurchaseOrder;
import com.zufe.procurement.m3.order.entity.PurchaseOrderItem;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderItemMapper;
import com.zufe.procurement.m3.order.mapper.PurchaseOrderMapper;
import com.zufe.procurement.m6.price.entity.PriceReference;
import com.zufe.procurement.m6.price.entity.PriceStatSnapshot;
import com.zufe.procurement.m6.price.mapper.PriceReferenceMapper;
import com.zufe.procurement.m6.price.mapper.PriceStatSnapshotMapper;
import com.zufe.procurement.m6.price.vo.EvidenceVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 审批证据面板（M6-4-1 / M6-4-2）。
 * 给审批人（采购主管）在一屏内看到：本行单价、下单时选用参考价与生效日、
 * 偏离率与等级、历史成交均价与最近成交价（含来源单号）。
 * 无参考价或无样本时明确提示缺口，不用其他数据冒充（R6、不伪造）。
 */
@Service
public class PriceEvidenceService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PriceReferenceMapper priceReferenceMapper;
    private final PriceStatSnapshotMapper snapshotMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final PriceDeviationService deviationService;

    public PriceEvidenceService(PurchaseOrderMapper orderMapper,
                                PurchaseOrderItemMapper orderItemMapper,
                                PriceReferenceMapper priceReferenceMapper,
                                PriceStatSnapshotMapper snapshotMapper,
                                MaterialMapper materialMapper,
                                SupplierMapper supplierMapper,
                                PriceDeviationService deviationService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.priceReferenceMapper = priceReferenceMapper;
        this.snapshotMapper = snapshotMapper;
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.deviationService = deviationService;
    }

    public EvidenceVO orderEvidence(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "采购订单不存在");
        }
        EvidenceVO vo = new EvidenceVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setDeviationFlag(order.getDeviationFlag());
        Supplier supplier = supplierMapper.selectById(order.getSupplierId());
        vo.setSupplierName(supplier == null ? null : supplier.getSupplierName());

        List<PurchaseOrderItem> items = orderItemMapper.selectByOrderId(orderId);
        Map<Long, Material> materials = new HashMap<>();
        for (Material material : materialMapper.selectList(
                new LambdaQueryWrapper<Material>().isNotNull(Material::getId))) {
            materials.put(material.getId(), material);
        }
        // 下单时点（订单创建日）的参考价——审批要还原决策时点；
        // 用 as-of 选价（不限 status）：当时生效的版本现在可能已停用
        LocalDate baseDate = order.getCreatedAt() == null
                ? LocalDate.now() : order.getCreatedAt().toLocalDate();

        List<EvidenceVO.EvidenceRow> rows = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            EvidenceVO.EvidenceRow row = new EvidenceVO.EvidenceRow();
            row.setItemId(item.getId());
            Material material = materials.get(item.getMaterialId());
            row.setMaterialNo(material == null ? null : material.getMaterialNo());
            row.setMaterialName(material == null ? null : material.getMaterialName());
            row.setSpec(material == null ? null : material.getSpec());
            row.setQuantity(item.getQuantity());
            row.setUnitPrice(item.getUnitPrice());
            row.setAmount(item.getAmount());

            PriceReference ref = priceReferenceMapper.selectEffectiveAsOf(item.getMaterialId(), baseDate);
            if (ref != null) {
                row.setRefPrice(ref.getRefPrice());
                row.setRefEffectiveDate(ref.getEffectiveDate().toString());
            }
            row.setDeviationRate(item.getDeviationRate());
            row.setDeviationLevel(item.getDeviationLevel());
            row.setDeviationLevelName(deviationService.levelName(item.getDeviationLevel()));

            List<PriceStatSnapshot> snapshots = snapshotMapper.selectByMaterialSupplier(
                    item.getMaterialId(), 0L);
            for (PriceStatSnapshot snapshot : snapshots) {
                if (Objects.equals(snapshot.getStatType(), PriceStatSnapshot.STAT_AVG)) {
                    row.setAvgPrice(snapshot.getStatValue());
                    row.setAvgSampleCount(snapshot.getSampleCount());
                    row.setStatWindow(snapshot.getStatWindow());
                } else if (Objects.equals(snapshot.getStatType(), PriceStatSnapshot.STAT_LAST)) {
                    row.setLastPrice(snapshot.getStatValue());
                    row.setLastBillNo(snapshot.getSourceBillNo());
                }
            }
            row.setNote(buildNote(ref, snapshots));
            rows.add(row);
        }
        vo.setRows(rows);
        return vo;
    }

    /** 证据缺口提示：缺参考价 / 缺成交样本分别说明，不伪造 */
    private String buildNote(PriceReference ref, List<PriceStatSnapshot> snapshots) {
        boolean hasAvg = false;
        boolean hasLast = false;
        for (PriceStatSnapshot snapshot : snapshots) {
            if (Objects.equals(snapshot.getStatType(), PriceStatSnapshot.STAT_AVG)) {
                hasAvg = true;
            } else if (Objects.equals(snapshot.getStatType(), PriceStatSnapshot.STAT_LAST)) {
                hasLast = true;
            }
        }
        if (ref == null && !hasAvg && !hasLast) {
            return "无生效参考价、无历史成交样本";
        }
        if (ref == null) {
            return "无生效参考价，偏离率未判定";
        }
        if (!hasAvg && !hasLast) {
            return "无历史成交样本";
        }
        return null;
    }
}
