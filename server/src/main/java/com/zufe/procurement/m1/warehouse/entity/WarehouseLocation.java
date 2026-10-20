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
 * 库位，对应 warehouse_location，功能点 M1-3-2。
 * 库位挂在仓库下，location_no 在仓库内唯一（唯一键含 warehouse_id）。
 */
@Data
@TableName("warehouse_location")
public class WarehouseLocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private String locationNo;

    private String locationName;

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
