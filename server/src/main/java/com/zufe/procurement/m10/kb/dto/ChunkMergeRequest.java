package com.zufe.procurement.m10.kb.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 切块合并（M10-2-3 切块预览与调整的手工合并部分）。
 * 传入同一文档内相邻的切块 id（按 chunk_index 升序 contiguous），
 * 合并后重排序号并重建该文档索引；任意手工拆分本轮不做
 * （交互成本高、收益低，条款内硬切已覆盖超长条款场景）。
 */
@Data
public class ChunkMergeRequest {

    @NotEmpty(message = "切块 id 不能为空")
    private List<Long> chunkIds;
}
