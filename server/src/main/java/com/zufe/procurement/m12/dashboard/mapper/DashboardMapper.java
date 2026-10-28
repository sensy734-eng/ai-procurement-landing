package com.zufe.procurement.m12.dashboard.mapper;

import com.zufe.procurement.m12.dashboard.vo.PurchaseSummaryRow;
import com.zufe.procurement.m12.dashboard.vo.StockSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 后台汇总与看板聚合查询（M12-2、M12-3）。
 *
 * 全部是只读聚合，不在内存里做二次汇总——图表数字要能与列表页逐行对上，
 * 聚合理应在数据库算完。数据范围参数由 Service 统一解析后传入：
 * deptIds / warehouseIds 为 null 表示不过滤（与页面查询的注入语义一致），
 * 空集合不会传进来（Service 层已判空）。
 */
@Mapper
public interface DashboardMapper {

    // ================= M12-3-1 订单状态分布 =================

    /**
     * 按状态桶聚合。桶的定义集中在 SQL 里，Service 只负责命名与排序：
     * 待审批 = lifecycle 2；待交付/部分交付/已完成 = lifecycle 3 且 fulfill 1/2/3；
     * 已关闭 = lifecycle 4 或 fulfill 4；其余（草稿 1、作废 5）归 OTHER。
     */
    @Select("""
            <script>
            SELECT CASE
                     WHEN lifecycle_status = 2 THEN 'PENDING_APPROVAL'
                     WHEN lifecycle_status = 3 AND fulfill_status = 1 THEN 'PENDING_DELIVERY'
                     WHEN lifecycle_status = 3 AND fulfill_status = 2 THEN 'PARTIAL'
                     WHEN lifecycle_status = 3 AND fulfill_status = 3 THEN 'DONE'
                     WHEN lifecycle_status = 4 OR fulfill_status = 4 THEN 'CLOSED'
                     WHEN lifecycle_status = 1 THEN 'DRAFT'
                     WHEN lifecycle_status = 5 THEN 'VOIDED'
                     ELSE 'OTHER'
                   END AS bucket,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(total_amount), 0) AS amount
            FROM purchase_order
            WHERE is_deleted = 0
            <if test="startDate != null"> AND order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND order_date &lt;= #{endDate} </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY bucket
            </script>
            """)
    List<BucketRow> statusDistribution(@Param("deptIds") Collection<Long> deptIds,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    // ================= M12-3-2 月度趋势 =================

    /** 已成交订单（lifecycle 3/4 且 audit 2）按月聚合，月份补零在 Service 完成 */
    @Select("""
            <script>
            SELECT DATE_FORMAT(order_date, '%Y-%m') AS month,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(total_amount), 0) AS amount
            FROM purchase_order
            WHERE is_deleted = 0 AND lifecycle_status IN (3, 4) AND audit_status = 2
            <if test="startDate != null"> AND order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND order_date &lt;= #{endDate} </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY DATE_FORMAT(order_date, '%Y-%m')
            ORDER BY month
            </script>
            """)
    List<MonthRow> monthlyTrend(@Param("deptIds") Collection<Long> deptIds,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    // ================= M12-3-3 供应商金额排名 =================

    @Select("""
            <script>
            SELECT o.supplier_id AS supplierId, s.supplier_no AS supplierNo, s.supplier_name AS supplierName,
                   COUNT(*) AS orderCount, COALESCE(SUM(o.total_amount), 0) AS amount
            FROM purchase_order o
            LEFT JOIN supplier s ON s.id = o.supplier_id
            WHERE o.is_deleted = 0 AND o.lifecycle_status IN (3, 4) AND o.audit_status = 2
            <if test="startDate != null"> AND o.order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND o.order_date &lt;= #{endDate} </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND o.dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY o.supplier_id, s.supplier_no, s.supplier_name
            ORDER BY amount DESC, o.supplier_id
            LIMIT #{topN}
            </script>
            """)
    List<SupplierRow> supplierRank(@Param("deptIds") Collection<Long> deptIds,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate,
                                   @Param("topN") int topN);

    /** 区间内已成交总金额（占比分母）与供应商家数：不受 Top-N 截断影响 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(total_amount), 0) AS amount, COUNT(DISTINCT supplier_id) AS supplierCount
            FROM purchase_order
            WHERE is_deleted = 0 AND lifecycle_status IN (3, 4) AND audit_status = 2
            <if test="startDate != null"> AND order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND order_date &lt;= #{endDate} </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            </script>
            """)
    TotalRow tradedTotal(@Param("deptIds") Collection<Long> deptIds,
                         @Param("startDate") LocalDate startDate,
                         @Param("endDate") LocalDate endDate);

    // ================= M12-2-1 采购单据汇总 =================

    /** 按状态分组：分组名由三状态组合给出，与列表页三列一致 */
    @Select("""
            <script>
            SELECT CONCAT(lifecycle_status, '-', audit_status, '-', fulfill_status) AS groupKey,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(total_qty), 0) AS qty,
                   COALESCE(SUM(total_amount), 0) AS amount
            FROM purchase_order
            WHERE is_deleted = 0
            <if test="startDate != null"> AND order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND order_date &lt;= #{endDate} </if>
            <if test="lifecycleStatus != null"> AND lifecycle_status = #{lifecycleStatus} </if>
            <if test="auditStatus != null"> AND audit_status = #{auditStatus} </if>
            <if test="fulfillStatus != null"> AND fulfill_status = #{fulfillStatus} </if>
            <if test="supplierId != null"> AND supplier_id = #{supplierId} </if>
            <if test="materialId != null">
              AND id IN (SELECT order_id FROM purchase_order_item
                         WHERE is_deleted = 0 AND material_id = #{materialId})
            </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY lifecycle_status, audit_status, fulfill_status
            ORDER BY orderCount DESC, lifecycle_status
            </script>
            """)
    List<PurchaseSummaryRow> summaryByStatus(@Param("deptIds") Collection<Long> deptIds,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate,
                                             @Param("supplierId") Long supplierId,
                                             @Param("materialId") Long materialId,
                                             @Param("lifecycleStatus") Integer lifecycleStatus,
                                             @Param("auditStatus") Integer auditStatus,
                                             @Param("fulfillStatus") Integer fulfillStatus);

    @Select("""
            <script>
            SELECT CAST(o.supplier_id AS CHAR) AS groupKey, s.supplier_name AS groupName,
                   COUNT(DISTINCT o.id) AS orderCount,
                   COALESCE(SUM(o.total_qty), 0) AS qty,
                   COALESCE(SUM(o.total_amount), 0) AS amount
            FROM purchase_order o
            LEFT JOIN supplier s ON s.id = o.supplier_id
            WHERE o.is_deleted = 0
            <if test="startDate != null"> AND o.order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND o.order_date &lt;= #{endDate} </if>
            <if test="lifecycleStatus != null"> AND o.lifecycle_status = #{lifecycleStatus} </if>
            <if test="auditStatus != null"> AND o.audit_status = #{auditStatus} </if>
            <if test="fulfillStatus != null"> AND o.fulfill_status = #{fulfillStatus} </if>
            <if test="supplierId != null"> AND o.supplier_id = #{supplierId} </if>
            <if test="materialId != null">
              AND o.id IN (SELECT order_id FROM purchase_order_item
                           WHERE is_deleted = 0 AND material_id = #{materialId})
            </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND o.dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY o.supplier_id, s.supplier_name
            ORDER BY amount DESC
            </script>
            """)
    List<PurchaseSummaryRow> summaryBySupplier(@Param("deptIds") Collection<Long> deptIds,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("supplierId") Long supplierId,
                                               @Param("materialId") Long materialId,
                                               @Param("lifecycleStatus") Integer lifecycleStatus,
                                               @Param("auditStatus") Integer auditStatus,
                                               @Param("fulfillStatus") Integer fulfillStatus);

    /**
     * 按物料分组：数量与金额取自明细行（口径是"这个物料买了多少、花了多少"），
     * 因此单据数与明细行数都按行去重统计，不能拿主单的 total_qty 凑。
     */
    @Select("""
            <script>
            SELECT CAST(i.material_id AS CHAR) AS groupKey, m.material_name AS groupName,
                   COUNT(DISTINCT o.id) AS orderCount, COUNT(*) AS itemCount,
                   COALESCE(SUM(i.quantity), 0) AS qty,
                   COALESCE(SUM(i.amount), 0) AS amount
            FROM purchase_order_item i
            JOIN purchase_order o ON o.id = i.order_id AND o.is_deleted = 0
            LEFT JOIN material m ON m.id = i.material_id
            WHERE i.is_deleted = 0
            <if test="startDate != null"> AND o.order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND o.order_date &lt;= #{endDate} </if>
            <if test="lifecycleStatus != null"> AND o.lifecycle_status = #{lifecycleStatus} </if>
            <if test="auditStatus != null"> AND o.audit_status = #{auditStatus} </if>
            <if test="fulfillStatus != null"> AND o.fulfill_status = #{fulfillStatus} </if>
            <if test="supplierId != null"> AND o.supplier_id = #{supplierId} </if>
            <if test="materialId != null"> AND i.material_id = #{materialId} </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND o.dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY i.material_id, m.material_name
            ORDER BY amount DESC
            </script>
            """)
    List<PurchaseSummaryRow> summaryByMaterial(@Param("deptIds") Collection<Long> deptIds,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("supplierId") Long supplierId,
                                               @Param("materialId") Long materialId,
                                               @Param("lifecycleStatus") Integer lifecycleStatus,
                                               @Param("auditStatus") Integer auditStatus,
                                               @Param("fulfillStatus") Integer fulfillStatus);

    @Select("""
            <script>
            SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS groupKey,
                   DATE_FORMAT(o.order_date, '%Y-%m') AS groupName,
                   COUNT(*) AS orderCount,
                   COALESCE(SUM(o.total_qty), 0) AS qty,
                   COALESCE(SUM(o.total_amount), 0) AS amount
            FROM purchase_order o
            WHERE o.is_deleted = 0
            <if test="startDate != null"> AND o.order_date &gt;= #{startDate} </if>
            <if test="endDate != null"> AND o.order_date &lt;= #{endDate} </if>
            <if test="lifecycleStatus != null"> AND o.lifecycle_status = #{lifecycleStatus} </if>
            <if test="auditStatus != null"> AND o.audit_status = #{auditStatus} </if>
            <if test="fulfillStatus != null"> AND o.fulfill_status = #{fulfillStatus} </if>
            <if test="supplierId != null"> AND o.supplier_id = #{supplierId} </if>
            <if test="materialId != null">
              AND o.id IN (SELECT order_id FROM purchase_order_item
                           WHERE is_deleted = 0 AND material_id = #{materialId})
            </if>
            <if test="deptIds != null and deptIds.size() > 0">
              AND o.dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            GROUP BY DATE_FORMAT(o.order_date, '%Y-%m')
            ORDER BY groupKey DESC
            </script>
            """)
    List<PurchaseSummaryRow> summaryByMonth(@Param("deptIds") Collection<Long> deptIds,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate,
                                            @Param("supplierId") Long supplierId,
                                            @Param("materialId") Long materialId,
                                            @Param("lifecycleStatus") Integer lifecycleStatus,
                                            @Param("auditStatus") Integer auditStatus,
                                            @Param("fulfillStatus") Integer fulfillStatus);

    // ================= M12-2-2 库存汇总 =================

    @Select("""
            <script>
            SELECT CAST(b.warehouse_id AS CHAR) AS groupKey, w.warehouse_name AS groupName,
                   COUNT(DISTINCT b.material_id) AS materialCount, COUNT(*) AS rowCount,
                   COALESCE(SUM(b.quantity), 0) AS quantity,
                   COALESCE(SUM(b.frozen_qty), 0) AS frozenQty,
                   COALESCE(SUM(b.reserved_qty), 0) AS reservedQty
            FROM stock_balance b
            LEFT JOIN warehouse w ON w.id = b.warehouse_id AND w.is_deleted = 0
            WHERE b.is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND b.warehouse_id IN
              <foreach collection="warehouseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            <if test="warehouseId != null"> AND b.warehouse_id = #{warehouseId} </if>
            <if test="categoryId != null">
              AND b.material_id IN (SELECT id FROM material
                                    WHERE is_deleted = 0 AND category_id = #{categoryId})
            </if>
            <if test="ownerNo != null and ownerNo != ''"> AND b.owner_no = #{ownerNo} </if>
            GROUP BY b.warehouse_id, w.warehouse_name
            ORDER BY quantity DESC
            </script>
            """)
    List<StockSummaryRow> stockByWarehouse(@Param("warehouseIds") Collection<Long> warehouseIds,
                                           @Param("warehouseId") Long warehouseId,
                                           @Param("categoryId") Long categoryId,
                                           @Param("ownerNo") String ownerNo);

    @Select("""
            <script>
            SELECT CAST(c.id AS CHAR) AS groupKey, c.category_name AS groupName,
                   COUNT(DISTINCT b.material_id) AS materialCount, COUNT(*) AS rowCount,
                   COALESCE(SUM(b.quantity), 0) AS quantity,
                   COALESCE(SUM(b.frozen_qty), 0) AS frozenQty,
                   COALESCE(SUM(b.reserved_qty), 0) AS reservedQty
            FROM stock_balance b
            JOIN material m ON m.id = b.material_id AND m.is_deleted = 0
            JOIN material_category c ON c.id = m.category_id AND c.is_deleted = 0
            WHERE b.is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND b.warehouse_id IN
              <foreach collection="warehouseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            <if test="warehouseId != null"> AND b.warehouse_id = #{warehouseId} </if>
            <if test="categoryId != null"> AND m.category_id = #{categoryId} </if>
            <if test="ownerNo != null and ownerNo != ''"> AND b.owner_no = #{ownerNo} </if>
            GROUP BY c.id, c.category_name
            ORDER BY quantity DESC
            </script>
            """)
    List<StockSummaryRow> stockByCategory(@Param("warehouseIds") Collection<Long> warehouseIds,
                                          @Param("warehouseId") Long warehouseId,
                                          @Param("categoryId") Long categoryId,
                                          @Param("ownerNo") String ownerNo);

    @Select("""
            <script>
            SELECT b.owner_no AS groupKey,
                   COALESCE(o.owner_name, IF(b.owner_type = 1, '公共库存', b.owner_no)) AS groupName,
                   COUNT(DISTINCT b.material_id) AS materialCount, COUNT(*) AS rowCount,
                   COALESCE(SUM(b.quantity), 0) AS quantity,
                   COALESCE(SUM(b.frozen_qty), 0) AS frozenQty,
                   COALESCE(SUM(b.reserved_qty), 0) AS reservedQty
            FROM stock_balance b
            LEFT JOIN owner_object o ON o.owner_no = b.owner_no AND o.is_deleted = 0
            WHERE b.is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND b.warehouse_id IN
              <foreach collection="warehouseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            <if test="warehouseId != null"> AND b.warehouse_id = #{warehouseId} </if>
            <if test="categoryId != null">
              AND b.material_id IN (SELECT id FROM material
                                    WHERE is_deleted = 0 AND category_id = #{categoryId})
            </if>
            <if test="ownerNo != null and ownerNo != ''"> AND b.owner_no = #{ownerNo} </if>
            GROUP BY b.owner_no, o.owner_name, b.owner_type
            ORDER BY quantity DESC
            </script>
            """)
    List<StockSummaryRow> stockByOwner(@Param("warehouseIds") Collection<Long> warehouseIds,
                                       @Param("warehouseId") Long warehouseId,
                                       @Param("categoryId") Long categoryId,
                                       @Param("ownerNo") String ownerNo);

    // ================= M12-3-4 库存不足 =================

    /**
     * 物料维度的库存合计（可限定仓库范围与分类），
     * 不足判定与缺口计算留给 Service——阈值来自物料档案，用 SQL 写死会把口径埋进字符串。
     */
    @Select("""
            <script>
            SELECT m.id AS materialId, m.material_no AS materialNo, m.material_name AS materialName,
                   m.spec AS spec, c.category_name AS categoryName, m.safety_stock AS safetyStock,
                   COALESCE(SUM(b.quantity), 0) AS quantity,
                   COALESCE(SUM(b.frozen_qty), 0) AS frozenQty,
                   COALESCE(SUM(b.reserved_qty), 0) AS reservedQty
            FROM material m
            LEFT JOIN material_category c ON c.id = m.category_id AND c.is_deleted = 0
            LEFT JOIN stock_balance b ON b.material_id = m.id AND b.is_deleted = 0
            <if test="warehouseIds != null and warehouseIds.size() > 0">
              AND b.warehouse_id IN
              <foreach collection="warehouseIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            <if test="warehouseId != null"> AND b.warehouse_id = #{warehouseId} </if>
            WHERE m.is_deleted = 0 AND m.status = 1
            <if test="categoryId != null"> AND m.category_id = #{categoryId} </if>
            GROUP BY m.id, m.material_no, m.material_name, m.spec, c.category_name, m.safety_stock
            ORDER BY m.id
            </script>
            """)
    List<StockAlertRow> stockAlertRows(@Param("warehouseIds") Collection<Long> warehouseIds,
                                       @Param("warehouseId") Long warehouseId,
                                       @Param("categoryId") Long categoryId);

    // ================= M12-1 首页计数 =================

    @Select("SELECT COUNT(*) FROM material WHERE is_deleted = 0")
    long countMaterials();

    @Select("SELECT COUNT(*) FROM supplier WHERE is_deleted = 0")
    long countSuppliers();

    @Select("SELECT COUNT(*) FROM material_category WHERE is_deleted = 0")
    long countCategories();

    @Select("SELECT COUNT(*) FROM dict_item WHERE is_deleted = 0")
    long countDictItems();

    @Select("SELECT COUNT(*) FROM sys_user WHERE is_deleted = 0")
    long countUsers();

    @Select("SELECT COUNT(*) FROM sys_role WHERE is_deleted = 0")
    long countRoles();

    @Select("SELECT COUNT(*) FROM kb_document WHERE is_deleted = 0")
    long countKbDocuments();

    @Select("SELECT COUNT(*) FROM sys_operation_log WHERE is_deleted = 0")
    long countOperationLogs();

    @Select("SELECT COUNT(*) FROM ai_query_audit WHERE is_deleted = 0")
    long countQueryAudits();

    /** 某人的待办审批任务数（M2 申请与 M3 订单共用审批任务表） */
    @Select("""
            <script>
            SELECT COUNT(*) FROM approval_task
            WHERE is_deleted = 0 AND task_status = 1 AND approver_id = #{approverId}
            <if test="billTypes != null and billTypes.size() > 0">
              AND bill_type IN
              <foreach collection="billTypes" item="type" open="(" separator="," close=")">#{type}</foreach>
            </if>
            </script>
            """)
    long countPendingTasks(@Param("approverId") Long approverId,
                           @Param("billTypes") Collection<Integer> billTypes);

    /** 某月已成交金额（lifecycle 3/4 且 audit 2），按部门范围过滤 */
    @Select("""
            <script>
            SELECT COALESCE(SUM(total_amount), 0) FROM purchase_order
            WHERE is_deleted = 0 AND lifecycle_status IN (3, 4) AND audit_status = 2
              AND order_date &gt;= #{startDate} AND order_date &lt;= #{endDate}
            <if test="deptIds != null and deptIds.size() > 0">
              AND dept_id IN
              <foreach collection="deptIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            </script>
            """)
    BigDecimal tradedAmountBetween(@Param("deptIds") Collection<Long> deptIds,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    // ================= 投影行 =================

    /** 状态分布中间行 */
    class BucketRow {

        private String bucket;

        private Integer orderCount;

        private BigDecimal amount;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public Integer getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Integer orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    /** 月度趋势中间行 */
    class MonthRow {

        private String month;

        private Integer orderCount;

        private BigDecimal amount;

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public Integer getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Integer orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    /** 供应商排名中间行 */
    class SupplierRow {

        private Long supplierId;

        private String supplierNo;

        private String supplierName;

        private Integer orderCount;

        private BigDecimal amount;

        public Long getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(Long supplierId) {
            this.supplierId = supplierId;
        }

        public String getSupplierNo() {
            return supplierNo;
        }

        public void setSupplierNo(String supplierNo) {
            this.supplierNo = supplierNo;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public Integer getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Integer orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    /** 总金额与供应商家数 */
    class TotalRow {

        private BigDecimal amount;

        private Integer supplierCount;

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public Integer getSupplierCount() {
            return supplierCount;
        }

        public void setSupplierCount(Integer supplierCount) {
            this.supplierCount = supplierCount;
        }
    }

    /** 库存不足中间行（判定前） */
    class StockAlertRow {

        private Long materialId;

        private String materialNo;

        private String materialName;

        private String spec;

        private String categoryName;

        private BigDecimal safetyStock;

        private BigDecimal quantity;

        private BigDecimal frozenQty;

        private BigDecimal reservedQty;

        public Long getMaterialId() {
            return materialId;
        }

        public void setMaterialId(Long materialId) {
            this.materialId = materialId;
        }

        public String getMaterialNo() {
            return materialNo;
        }

        public void setMaterialNo(String materialNo) {
            this.materialNo = materialNo;
        }

        public String getMaterialName() {
            return materialName;
        }

        public void setMaterialName(String materialName) {
            this.materialName = materialName;
        }

        public String getSpec() {
            return spec;
        }

        public void setSpec(String spec) {
            this.spec = spec;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public BigDecimal getSafetyStock() {
            return safetyStock;
        }

        public void setSafetyStock(BigDecimal safetyStock) {
            this.safetyStock = safetyStock;
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
