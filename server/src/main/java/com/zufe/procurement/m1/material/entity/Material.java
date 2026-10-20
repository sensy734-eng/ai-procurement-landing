package com.zufe.procurement.m1.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料主表，对应 material，功能点 M1-1-1 至 M1-1-4。
 * 编码逻辑删除后不复用：唯一键 uk_material_no 不含 is_deleted，
 * 因此查重必须连带已删除记录一起查（与 sys_user 同一处理）。
 */
@Data
@TableName("material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialNo;

    private String materialName;

    private Long categoryId;

    private String spec;

    private String brand;

    /** 计量单位，关联 dict_item.id（字典类型 unit），不建数据库外键 */
    private Long unitId;

    /** 物料类型：1 原材料、2 辅料、3 外购件、4 备件，字典类型 material_type */
    private Integer materialType;

    /** 预警下限，M5-5-1 库存不足判定基准 */
    private BigDecimal safetyStock;

    /** 启用状态：1 启用、2 停用；停用后不可在新单据选择（M1-1-4） */
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
    private LocalDateTime updatedAt;
}
