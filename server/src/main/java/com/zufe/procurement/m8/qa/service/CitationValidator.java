package com.zufe.procurement.m8.qa.service;

import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * 引用有效性校验（M8-3-3，同时补上轮次 9 留到本轮的 M10-5-3）。
 *
 * 三道校验，缺一即判无效：切块存在、切块确属所引文档、文档仍为启用状态。
 * 这样「引用了不存在的片段」或「引用了已停用文档」不会进入答案，
 * 也解释了为什么删文档后历史答案里的引用不能再点开原文。
 */
@Service
public class CitationValidator {

    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;

    public CitationValidator(KbChunkMapper chunkMapper, KbDocumentMapper documentMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    public Check verify(Long docId, Long chunkId) {
        Check check = new Check();
        if (docId == null || chunkId == null) {
            check.setValid(false);
            check.setReason("引用缺少文档或切块标识");
            return check;
        }
        KbChunk chunk = chunkMapper.selectById(chunkId);
        if (chunk == null) {
            check.setValid(false);
            check.setReason("切块不存在或已删除");
            return check;
        }
        if (!docId.equals(chunk.getDocId())) {
            check.setValid(false);
            check.setReason("切块不属于所引文档");
            return check;
        }
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            check.setValid(false);
            check.setReason("文档不存在或已删除");
            return check;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if (doc.getStatus() == null || doc.getStatus() != 1
                || (doc.getEffectiveDate() != null && doc.getEffectiveDate().isAfter(today))
                || (doc.getExpireDate() != null && doc.getExpireDate().isBefore(today))) {
            check.setValid(false);
            check.setReason("文档已停用或失效，不参与默认召回");
            return check;
        }
        check.setValid(true);
        check.setReason("引用有效");
        check.setDocName(doc.getDocName());
        check.setDocVersion(doc.getVersion());
        return check;
    }

    @Data
    public static class Check {
        private Boolean valid;
        private String reason;
        private String docName;
        private String docVersion;
    }
}
