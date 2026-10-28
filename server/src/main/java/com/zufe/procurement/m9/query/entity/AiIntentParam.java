package com.zufe.procurement.m9.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意图参数规格（M9-2-4），对应 ai_intent_param。
 *
 * 白名单校验依据：参数编码必须在表内，操作符必须落在 operator_set，
 * 取值必须落在 enum_values（为 NULL 表示不做枚举限制）。
 * 任一项越界返回 InvalidParameter 并把被拒参数写进审计。
 */
@Data
@TableName("ai_intent_param")
public class AiIntentParam {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long intentId;
    private String paramCode;
    private String paramName;
    /** string、long、decimal、date、boolean、int */
    private String dataType;
    /** 允许的操作符集合，逗号分隔 */
    private String operatorSet;
    private Integer required;
    private String enumValues;
    private Integer sortNo;
    private String remark;
    private Integer isDeleted;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
