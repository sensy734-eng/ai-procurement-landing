package com.zufe.procurement.m10.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m10.kb.entity.KbChunk;
import com.zufe.procurement.m10.kb.entity.KbSparseIndex;
import com.zufe.procurement.m10.kb.mapper.KbChunkMapper;
import com.zufe.procurement.m10.kb.mapper.KbSparseIndexMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 索引自愈（M10-5-1 的运维补充）。
 *
 * 倒排索引与向量是派生数据，清理脚本按口径把索引清空、只保留文档与切块。
 * 这样启动后第一次检索会一无所获，演示时很像「知识库坏了」。
 * 因此在启动时判断一次：有切块但没有倒排索引，就按文档全量重建一次。
 *
 * 只写派生表，不动业务数据；重建失败也只记日志，不影响服务启动。
 */
@Slf4j
@Component
public class KbIndexBootstrap implements ApplicationRunner {

    private final KbIndexService indexService;
    private final KbChunkMapper chunkMapper;
    private final KbSparseIndexMapper sparseIndexMapper;

    public KbIndexBootstrap(KbIndexService indexService, KbChunkMapper chunkMapper,
                            KbSparseIndexMapper sparseIndexMapper) {
        this.indexService = indexService;
        this.chunkMapper = chunkMapper;
        this.sparseIndexMapper = sparseIndexMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getIsDeleted, 0));
            Long indexCount = sparseIndexMapper.selectCount(new LambdaQueryWrapper<KbSparseIndex>());
            if (chunkCount != null && chunkCount > 0 && (indexCount == null || indexCount == 0)) {
                KbIndexService.IndexBuildResult result = indexService.rebuildAll();
                log.info("知识库倒排索引为空，已启动时重建：文档 {} 篇、切块 {} 个、词元 {} 条，耗时 {} ms",
                        result.getDocCount(), result.getChunkCount(), result.getTokenCount(),
                        result.getElapsedMs());
            }
        } catch (Exception e) {
            log.warn("知识库索引自愈失败，不影响启动：{}", e.getMessage());
        }
    }
}
