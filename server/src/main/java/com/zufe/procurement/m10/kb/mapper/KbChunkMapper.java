package com.zufe.procurement.m10.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {
    @Select("SELECT * FROM kb_chunk WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    KbChunk selectForUpdate(Long id);

    /** 参与默认召回的切块 id（所属文档启用且未删除）——BM25 的文档总数 N 与候选过滤 */
    @Select("SELECT c.id FROM kb_chunk c JOIN kb_document d ON c.doc_id = d.id "
            + "WHERE c.is_deleted = 0 AND d.is_deleted = 0 AND d.status = 1 "
            + "AND (d.effective_date IS NULL OR d.effective_date <= CURRENT_DATE) "
            + "AND (d.expire_date IS NULL OR d.expire_date >= CURRENT_DATE)")
    List<Long> selectEnabledChunkIds();

    /** 参与默认召回切块的平均长度（BM25 的 avgdl） */
    @Select("SELECT AVG(c.char_count) FROM kb_chunk c JOIN kb_document d ON c.doc_id = d.id "
            + "WHERE c.is_deleted = 0 AND d.is_deleted = 0 AND d.status = 1 "
            + "AND (d.effective_date IS NULL OR d.effective_date <= CURRENT_DATE) "
            + "AND (d.expire_date IS NULL OR d.expire_date >= CURRENT_DATE)")
    Double avgEnabledCharCount();

    /** 参与默认召回且已向量化的切块（向量路全量候选，与稀疏路同条件对照） */
    @Select("SELECT c.* FROM kb_chunk c JOIN kb_document d ON c.doc_id = d.id "
            + "WHERE c.is_deleted = 0 AND d.is_deleted = 0 AND d.status = 1 AND c.embedding IS NOT NULL "
            + "AND (d.effective_date IS NULL OR d.effective_date <= CURRENT_DATE) "
            + "AND (d.expire_date IS NULL OR d.expire_date >= CURRENT_DATE)")
    List<KbChunk> selectEnabledWithEmbedding();

    /**
     * 物理删除单块（切块合并时删被合并块）。
     * 全局 logic-delete-field 会让 BaseMapper.deleteById 降级为逻辑删除，
     * 切块是文档的派生数据、随文档物理删除，必须走原生 DELETE。
     */
    @Delete("DELETE FROM kb_chunk WHERE id = #{id}")
    int deleteByIdPhysical(Long id);

    /** 物理删除文档全部切块（重新解析前清旧块、文档删除时清理） */
    @Delete("DELETE FROM kb_chunk WHERE doc_id = #{docId}")
    int deleteByDocIdPhysical(Long docId);
}
