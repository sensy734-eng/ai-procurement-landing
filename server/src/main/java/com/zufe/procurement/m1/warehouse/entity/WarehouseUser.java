package com.zufe.procurement.m1.warehouse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库使用人关系，对应 warehouse_user，功能点 M1-3-3。
 * 作为数据范围授权依据，与 sys_data_scope 的仓库维度配合使用。
 * 唯一键 uk_warehouse_user 不含 is_deleted，分配时物理删除后重建。
 */
@Data
@TableName("warehouse_user")
public class WarehouseUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long userId;

    /** 是否默认仓库：0 否、1 是 */
    private Integer isDefault;

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
