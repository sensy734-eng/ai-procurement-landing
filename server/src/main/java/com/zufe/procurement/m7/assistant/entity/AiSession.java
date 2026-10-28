package com.zufe.procurement.m7.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能会话（M7-1-2、M7-1-3），对应 ai_session。
 *
 * 口径：
 * - 一个用户可建多个会话，按 last_time 倒序展示；
 * - context_json 保存上一轮意图与实体，只用于指代消解（「它的供应商是谁」），
 *   不作为查询参数的可信来源，执行前仍走白名单校验；
 * - 会话与消息只增不删，审计留痕。
 */
@Data
@TableName("ai_session")
public class AiSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private Integer messageCount;
    /** 上一轮意图编码，供多轮指代与前端提示 */
    private String lastIntentCode;
    /** 上一轮实体与参数，JSON */
    private String contextJson;
    private LocalDateTime lastTime;
    private Integer isDeleted;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
