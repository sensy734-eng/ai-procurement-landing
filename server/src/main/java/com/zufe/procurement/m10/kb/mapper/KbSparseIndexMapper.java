package com.zufe.procurement.m10.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m10.kb.entity.KbSparseIndex;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbSparseIndexMapper extends BaseMapper<KbSparseIndex> {

    /**
     * 物理删除某切块的倒排索引（索引重建先删后建）。
     * 全局 logic-delete-field 会让 BaseMapper.delete 降级为逻辑删除，
     * 而 uk_sparse_chunk_token 唯一键不含 is_deleted，旧行不走物理删除
     * 会让重建时的 INSERT 直接撞唯一键（DuplicateKeyException）。
     */
    @Delete("DELETE FROM kb_sparse_index WHERE chunk_id = #{chunkId}")
    int deleteByChunkIdPhysical(Long chunkId);
}
