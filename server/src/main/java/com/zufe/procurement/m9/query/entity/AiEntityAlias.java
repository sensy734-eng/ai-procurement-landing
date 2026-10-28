package com.zufe.procurement.m9.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体别名（M9-3-1），对应 ai_entity_alias。
 * 支撑口语化名称到业务主键的归一；同一别名对多个实体时返回 Ambiguous。
 */
@Data
@TableName("ai_entity_alias")
public class AiEntityAlias {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 1 物料、2 供应商、3 仓库 */
    private Integer entityType;
    private Long entityId;
    private String entityNo;
    private String aliasName;
    /** 1 手工维护、2 拒答反馈补充 */
    private Integer source;
    private Integer hitCount;
    private String remark;
    private Integer isDeleted;
    @Version
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
