package com.zufe.procurement.m10.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 稀疏检索倒排索引（M10-3-2），对应 kb_sparse_index。
 * token 为中文字符二元组（与检索端分词一致），tf 为词元在切块内的词频。
 * 索引随文档解析/校对/合并/重建全量重写该文档的条目（增量更新的单位是文档）。
 */
@Data
@TableName("kb_sparse_index")
public class KbSparseIndex {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chunkId;
    private String token;
    private Integer tf;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
