package com.zufe.procurement.m10.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m10.kb.dto.ChunkMergeRequest;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import com.zufe.procurement.m10.kb.mapper.KbSparseIndexMapper;
import com.zufe.procurement.m10.kb.service.KbIndexService.IndexBuildResult;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 切块查看与手工合并（M10-2-3、M10-4-4）。
 *
 * 口径：合并只接受同一文档内按序号相邻的切块（保护条款边界——不相邻的块
 * 中间隔着其他条款，合并会制造跨条款碎片）；合并后重排序号并重建该文档
 * 倒排与向量索引（索引增量单位是文档，M10-3-3）。
 */
@Service
public class KbChunkService {

    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final KbSparseIndexMapper sparseIndexMapper;
    private final KbIndexService indexService;
    private final OperationLogService operationLogService;

    public KbChunkService(KbChunkMapper chunkMapper,
                          KbDocumentMapper documentMapper,
                          KbSparseIndexMapper sparseIndexMapper,
                          KbIndexService indexService,
                          OperationLogService operationLogService) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.sparseIndexMapper = sparseIndexMapper;
        this.indexService = indexService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public IndexBuildResult merge(Long docId, ChunkMergeRequest request) {
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "知识文档不存在");
        }
        List<Long> ids = new ArrayList<>(request.getChunkIds());
        ids.sort(Comparator.naturalOrder());
        List<KbChunk> chunks = chunkMapper.selectBatchIds(ids);
        chunks.sort(Comparator.comparing(KbChunk::getChunkIndex));
        if (chunks.size() != ids.size()) {
            throw new BizException(ResultCode.NOT_FOUND, "存在不存在的切块");
        }
        for (KbChunk chunk : chunks) {
            if (!docId.equals(chunk.getDocId())) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "切块不属于该文档");
            }
        }
        if (chunks.size() < 2) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "合并至少需要两个相邻切块");
        }
        for (int i = 1; i < chunks.size(); i++) {
            if (chunks.get(i).getChunkIndex() - chunks.get(i - 1).getChunkIndex() != 1) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "只能合并序号相邻的切块（条款边界保护）");
            }
        }

        KbChunk first = chunks.get(0);
        StringBuilder text = new StringBuilder();
        for (KbChunk chunk : chunks) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(chunk.getChunkText());
        }
        for (int i = 1; i < chunks.size(); i++) {
            sparseIndexMapper.deleteByChunkIdPhysical(chunks.get(i).getId());
            chunkMapper.deleteByIdPhysical(chunks.get(i).getId());
        }
        first.setChunkText(text.toString());
        first.setCharCount(text.length());
        chunkMapper.updateById(first);

        // 重排序号 1..n（升序处理，目标序号不大于当前序号，不会撞唯一键）
        List<KbChunk> remaining = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId)
                .orderByAsc(KbChunk::getChunkIndex));
        for (int i = 0; i < remaining.size(); i++) {
            if (!remaining.get(i).getChunkIndex().equals(i + 1)) {
                KbChunk row = new KbChunk();
                row.setId(remaining.get(i).getId());
                row.setChunkIndex(i + 1);
                chunkMapper.updateById(row);
            }
        }
        IndexBuildResult result = indexService.rebuildDoc(docId);

        doc.setChunkCount(remaining.size());
        documentMapper.updateById(doc);
        CurrentUser user = AuthContext.get();
        operationLogService.record("M10", "chunk-merge", "kb_document", docId, doc.getDocName(),
                "合并 " + chunks.size() + " 个相邻切块，剩余 " + remaining.size() + " 个"
                        + (user == null ? "" : "，操作人 " + user.getRealName()));
        return result;
    }
}
