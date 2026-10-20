package com.zufe.procurement.m11.param.entity;

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
 * 系统参数，对应 sys_param，覆盖功能点 M11-4-1 价格偏离阈值、
 * M11-4-2 库存预警下限、M11-4-3 单据编号规则，以及 M6、M8、M9 的口径参数。
 * 值统一以字符串存放，按 param_type 解释，读取方必须按类型解析后再用。
 */
@Data
@TableName("sys_param")
public class SysParam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paramCode;

    private String paramName;

    private String paramValue;

    /** string、int、decimal、boolean、json */
    private String paramType;

    private String paramGroup;

    private Integer editable;

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
