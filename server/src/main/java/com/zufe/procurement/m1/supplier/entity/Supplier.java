package com.zufe.procurement.m1.supplier.entity;

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
 * 供应商主表，对应 supplier，功能点 M1-2-1 至 M1-2-3。
 * supplier_type 取值 1 生产厂家、2 经销商、3 服务商，字典类型 supplier_type。
 */
@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String supplierNo;

    private String supplierName;

    private Integer supplierType;

    private String taxNo;

    private String address;

    private String phone;

    private String email;

    private String bankName;

    private String bankAccount;

    /** 启用状态：1 启用、2 停用；停用后不可用于新订单（M1-2-3） */
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
