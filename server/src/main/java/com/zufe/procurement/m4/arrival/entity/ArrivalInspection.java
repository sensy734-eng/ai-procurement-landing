package com.zufe.procurement.m4.arrival.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 到货验收单，对应 arrival_inspection，功能点 M4-1 到货登记、M4-2 验收判定。
 * 本项目无独立质检单，本表同时承担「货到了」与「货合格了」两件事：
 * 到货登记记 arrival_qty（明细行），合格判定记 inspection_result 与
 * qualified/unqualified_qty，两者分列不混算。
 *
 * 业务口径（轮次 6 裁定）：
 * - 到货配额与入库配额是两个口径：到货不超「订单量 − 已到货量」，
 *   入库不超「订单量 − delivered_qty − 已生成未确认入库量」；
 * - 整单验收结果由明细行重算：全行无不合格 → 1 合格；存在不合格且打了
 *   让步标记 → 3 让步接收；存在不合格未让步 → 2 不合格；
 * - 不合格数量不生成入库单；让步接收是唯一例外（不合格但同意接收），
 *   否则让步接收没有业务意义；
 * - 草稿也占到货配额——两人同时建草稿不能超发。
 */
@Data
@TableName("arrival_inspection")
public class ArrivalInspection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inspectionNo;

    private Long orderId;

    /** 来源订单号，冗余，列表与导出免 join */
    private String orderNo;

    private Long supplierId;

    private LocalDate arrivalDate;

    private Long warehouseId;

    /** 验收方式：1 免检、2 抽样、3 全检；只登记，不做抽检规则计算（M4-2-1） */
    private Integer inspectionMode;

    /** 验收结果：1 合格、2 不合格、3 让步接收；由明细行重算，不人工选择 */
    private Integer inspectionResult;

    private BigDecimal qualifiedQty;

    private BigDecimal unqualifiedQty;

    /** 让步接收标记：0 否、1 是（M4-2-4），仅采购主管可打 */
    private Integer concessionFlag;

    private String concessionReason;

    private Long inspectorId;

    /** 验收单状态：1 草稿、2 已验收 */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
