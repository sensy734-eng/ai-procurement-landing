package com.zufe.procurement.m9.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意图白名单（M9-1-3），对应 ai_intent。
 *
 * 一版只做五类预设意图，每类映射到一个确定的后端查询服务；
 * 模型或规则只负责把问句解析成这张表允许的参数，不参与选表选字段。
 */
@Data
@TableName("ai_intent")
public class AiIntent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String intentCode;
    private String intentName;
    /** 映射的后端查询服务标识，白名单内固定值 */
    private String serviceCode;
    /** 示例问句，多个以竖线分隔 */
    private String questionSamples;
    private Integer enabled;
    private Integer sortNo;
    private String remark;
    private Integer isDeleted;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
