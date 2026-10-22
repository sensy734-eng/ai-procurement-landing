package com.zufe.procurement.m5.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m5.stock.entity.StockBalance;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 库存余额 Mapper。库存唯一写入点（R2）的落地处：只有入库单确认
 * （increase，change 为正）与作废冲减（decrease，change 为负）
 * 两个入口调本 Mapper 的写法，其他路径一律只读。
 */
public interface StockBalanceMapper extends BaseMapper<StockBalance> {

    /** 按唯一键（物料+仓库+批次+归属）查余额行；系统无删除库存行的端点，最多一条 */
    @Select("""
            SELECT * FROM stock_balance
            WHERE material_id = #{materialId} AND warehouse_id = #{warehouseId}
              AND batch_no = #{batchNo} AND owner_type = #{ownerType} AND owner_id = #{ownerId}
            LIMIT 1
            """)
    StockBalance selectByUk(@Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId,
                            @Param("batchNo") String batchNo, @Param("ownerType") Integer ownerType,
                            @Param("ownerId") Long ownerId);

    /**
     * 乐观锁增减：version 不匹配时影响行数为 0，由调用方重试或报并发冲突（M5-5-4）。
     * 不用 MP 实体更新是为了把唯一键定位与版本竞争压成一条 SQL，避免 select-then-update 竞态。
     */
    @Update("""
            UPDATE stock_balance
            SET quantity = quantity + #{delta}, version = version + 1
            WHERE id = #{id} AND version = #{version}
            """)
    int changeQty(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("version") Integer version);

    /**
     * 并发首插兜底：两笔并发入库同时 insert 同一唯一键行，后到者撞键，
     * 捕获后走「重新 select + 乐观锁更新」路径，保证不超发不覆盖。
     */
    @Insert("""
            INSERT INTO stock_balance (material_id, warehouse_id, batch_no, owner_type, owner_id, owner_no,
                                       quantity, frozen_qty, reserved_qty)
            VALUES (#{materialId}, #{warehouseId}, #{batchNo}, #{ownerType}, #{ownerId}, #{ownerNo},
                    #{delta}, 0, 0)
            """)
    int insertInitial(@Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId,
                      @Param("batchNo") String batchNo, @Param("ownerType") Integer ownerType,
                      @Param("ownerId") Long ownerId, @Param("ownerNo") String ownerNo,
                      @Param("delta") BigDecimal delta);

    /** 按物料汇总当前库存（预警用）：数量、冻结、预留一次算出，可用量在 Service 层现算（R3） */
    @Select("""
            <script>
            SELECT material_id AS materialId, COALESCE(SUM(quantity), 0) AS quantity,
                   COALESCE(SUM(frozen_qty), 0) AS frozenQty, COALESCE(SUM(reserved_qty), 0) AS reservedQty
            FROM stock_balance
            WHERE is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND warehouse_id IN
              <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
            </if>
            <if test="materialIds != null and materialIds.size() > 0">
              AND material_id IN
              <foreach collection="materialIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            GROUP BY material_id
            </script>
            """)
    List<MaterialQtyRow> sumByMaterial(@Param("warehouseIds") Collection<Long> warehouseIds,
                                       @Param("materialIds") Collection<Long> materialIds);

    /** 按物料+仓库+归属汇总的投影行（M5-3-3 归属分组） */
    @Select("""
            <script>
            SELECT material_id AS materialId, warehouse_id AS warehouseId, owner_type AS ownerType,
                   owner_id AS ownerId, owner_no AS ownerNo, COALESCE(SUM(quantity), 0) AS quantity,
                   COALESCE(SUM(frozen_qty), 0) AS frozenQty, COALESCE(SUM(reserved_qty), 0) AS reservedQty
            FROM stock_balance
            WHERE is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND warehouse_id IN
              <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
            </if>
            <if test="materialIds != null and materialIds.size() > 0">
              AND material_id IN
              <foreach collection="materialIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            GROUP BY material_id, warehouse_id, owner_type, owner_id, owner_no
            </script>
            """)
    List<GroupQtyRow> sumByMaterialWarehouseOwner(@Param("warehouseIds") Collection<Long> warehouseIds,
                                                  @Param("materialIds") Collection<Long> materialIds);

    /** 按物料+仓库汇总的投影行（M5-3-1 余额列表） */
    @Select("""
            <script>
            SELECT material_id AS materialId, warehouse_id AS warehouseId,
                   COALESCE(SUM(quantity), 0) AS quantity,
                   COALESCE(SUM(frozen_qty), 0) AS frozenQty, COALESCE(SUM(reserved_qty), 0) AS reservedQty
            FROM stock_balance
            WHERE is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND warehouse_id IN
              <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
            </if>
            <if test="materialIds != null and materialIds.size() > 0">
              AND material_id IN
              <foreach collection="materialIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            GROUP BY material_id, warehouse_id
            </script>
            """)
    List<MaterialWarehouseQtyRow> sumByMaterialWarehouse(@Param("warehouseIds") Collection<Long> warehouseIds,
                                                         @Param("materialIds") Collection<Long> materialIds);

    /**
     * 按物料+仓库+批次汇总的投影行（M5-3-1 列表的批次展开）：
     * 物料+仓库行带该维度下的批次构成，台账页面展开显示；
     * 与汇总行同范围参数，保证展开合计 = 行数量。
     */
    @Select("""
            <script>
            SELECT material_id AS materialId, warehouse_id AS warehouseId, batch_no AS batchNo,
                   COALESCE(SUM(quantity), 0) AS quantity,
                   COALESCE(SUM(frozen_qty), 0) AS frozenQty, COALESCE(SUM(reserved_qty), 0) AS reservedQty
            FROM stock_balance
            WHERE is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND warehouse_id IN
              <foreach collection="warehouseIds" item="wid" open="(" separator="," close=")">#{wid}</foreach>
            </if>
            <if test="materialIds != null and materialIds.size() > 0">
              AND material_id IN
              <foreach collection="materialIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
            </if>
            GROUP BY material_id, warehouse_id, batch_no
            </script>
            """)
    List<BatchQtyRow> sumByMaterialWarehouseBatch(@Param("warehouseIds") Collection<Long> warehouseIds,
                                                  @Param("materialIds") Collection<Long> materialIds);

    /** 按物料+仓库+批次汇总的投影行 */
    public class BatchQtyRow {
        private Long materialId;
        private Long warehouseId;
        private String batchNo;
        private BigDecimal quantity;
        private BigDecimal frozenQty;
        private BigDecimal reservedQty;

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

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getFrozenQty() {
            return frozenQty;
        }

        public void setFrozenQty(BigDecimal frozenQty) {
            this.frozenQty = frozenQty;
        }

        public BigDecimal getReservedQty() {
            return reservedQty;
        }

        public void setReservedQty(BigDecimal reservedQty) {
            this.reservedQty = reservedQty;
        }
    }

    /** 按物料汇总的投影行 */
    public class MaterialQtyRow {
        private Long materialId;
        private BigDecimal quantity;
        private BigDecimal frozenQty;
        private BigDecimal reservedQty;

        public Long getMaterialId() {
            return materialId;
        }

        public void setMaterialId(Long materialId) {
            this.materialId = materialId;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getFrozenQty() {
            return frozenQty;
        }

        public void setFrozenQty(BigDecimal frozenQty) {
            this.frozenQty = frozenQty;
        }

        public BigDecimal getReservedQty() {
            return reservedQty;
        }

        public void setReservedQty(BigDecimal reservedQty) {
            this.reservedQty = reservedQty;
        }
    }

    /** 按物料+仓库+归属汇总的投影行 */
    public class GroupQtyRow {
        private Long materialId;
        private Long warehouseId;
        private Integer ownerType;
        private Long ownerId;
        private String ownerNo;
        private BigDecimal quantity;
        private BigDecimal frozenQty;
        private BigDecimal reservedQty;

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

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getFrozenQty() {
            return frozenQty;
        }

        public void setFrozenQty(BigDecimal frozenQty) {
            this.frozenQty = frozenQty;
        }

        public BigDecimal getReservedQty() {
            return reservedQty;
        }

        public void setReservedQty(BigDecimal reservedQty) {
            this.reservedQty = reservedQty;
        }
    }

    /** 按物料+仓库汇总的投影行 */
    public class MaterialWarehouseQtyRow {
        private Long materialId;
        private Long warehouseId;
        private BigDecimal quantity;
        private BigDecimal frozenQty;
        private BigDecimal reservedQty;

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

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getFrozenQty() {
            return frozenQty;
        }

        public void setFrozenQty(BigDecimal frozenQty) {
            this.frozenQty = frozenQty;
        }

        public BigDecimal getReservedQty() {
            return reservedQty;
        }

        public void setReservedQty(BigDecimal reservedQty) {
            this.reservedQty = reservedQty;
        }
    }
}
