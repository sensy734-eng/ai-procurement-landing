package com.zufe.procurement.m5.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m5.stock.entity.StockFlow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 库存流水 Mapper。流水只增不改（M5-4-1），对账（M5-4-4）与批次追溯
 * （M5-2-3）都走本 Mapper 的聚合查询。
 */
public interface StockFlowMapper extends BaseMapper<StockFlow> {

    /** 按来源入库单查流水（作废冲减与原入库记录同 source_id） */
    @Select("""
            SELECT * FROM stock_flow
            WHERE source_type = 1 AND source_id = #{inboundId} AND is_deleted = 0
            ORDER BY id
            """)
    List<StockFlow> selectBySourceId(@Param("inboundId") Long inboundId);

    /** 按批次号查流水（批次追溯 M5-2-3：批次 → 入库单 → 订单 → 供应商） */
    @Select("""
            SELECT f.* FROM stock_flow f
            WHERE f.batch_no = #{batchNo} AND f.is_deleted = 0
            ORDER BY f.id
            """)
    List<StockFlow> selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 对账：指定时间点前按「物料+仓库+批次+归属」汇总流水累计，
     * 与 stock_balance.quantity 逐行比对（M5-4-4）。
     */
    @Select("""
            <script>
            SELECT f.material_id AS materialId, f.warehouse_id AS warehouseId, f.batch_no AS batchNo,
                   f.owner_type AS ownerType, f.owner_id AS ownerId, f.owner_no AS ownerNo,
                   COALESCE(SUM(f.change_qty), 0) AS flowQty
            FROM stock_flow f
            WHERE f.is_deleted = 0 AND f.flow_time &lt;= #{asOf}
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND f.warehouse_id IN
              <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
            </if>
            <if test="materialIds != null and materialIds.size() > 0">
              AND f.material_id IN
              <foreach collection="materialIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            GROUP BY f.material_id, f.warehouse_id, f.batch_no, f.owner_type, f.owner_id, f.owner_no
            </script>
            """)
    List<FlowSumRow> sumChangeQtyByKey(@Param("asOf") LocalDateTime asOf,
                                       @Param("warehouseIds") Collection<Long> warehouseIds,
                                       @Param("materialIds") Collection<Long> materialIds);

    /** 对账用的流水累计投影行 */
    public class FlowSumRow {
        private Long materialId;
        private Long warehouseId;
        private String batchNo;
        private Integer ownerType;
        private Long ownerId;
        private String ownerNo;
        private java.math.BigDecimal flowQty;

        public Long getMaterialId() {
            return materialId;
        }

        public void setMaterialId(Long materialId) {
            this.materialId = materialId;
        }

        public Long getWarehouseId() {
            return warehouseId;
        }

        public void setWarehouseId(Long warehouseId) {
            this.warehouseId = warehouseId;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        public Integer getOwnerType() {
            return ownerType;
        }

        public void setOwnerType(Integer ownerType) {
            this.ownerType = ownerType;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(Long ownerId) {
            this.ownerId = ownerId;
        }

        public String getOwnerNo() {
            return ownerNo;
        }

        public void setOwnerNo(String ownerNo) {
            this.ownerNo = ownerNo;
        }

        public java.math.BigDecimal getFlowQty() {
            return flowQty;
        }

        public void setFlowQty(java.math.BigDecimal flowQty) {
            this.flowQty = flowQty;
        }
    }
}
