package com.zufe.procurement.m10.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.auth.CurrentUser;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.m10.kb.dto.RetrievalTestRequest;
import com.zufe.procurement.m10.kb.dto.TestStatQuery;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import com.zufe.procurement.m10.kb.entity.KbRetrievalTest;
import com.zufe.procurement.m10.kb.mapper.KbDocumentMapper;
import com.zufe.procurement.m10.kb.mapper.KbRetrievalTestMapper;
import com.zufe.procurement.m10.kb.service.KbRetrievalService.Hit;
import com.zufe.procurement.m10.kb.service.KbRetrievalService.RetrievalResult;
import com.zufe.procurement.m10.kb.vo.RetrievalHitVO;
import com.zufe.procurement.m10.kb.vo.RetrievalTestVO;
import com.zufe.procurement.m10.kb.vo.TestStatVO;
import com.zufe.procurement.m11.param.service.SysParamService;
import com.zufe.procurement.m11.system.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索测试台与命中率统计（M10-5-1、M10-5-2）。
 *
 * 口径：一次问句并行两路 Top-K，结果与命中判定落 kb_retrieval_test；
 * 命中判定以「期望切块」优先、「期望文档」兜底；未给期望时只展示不判定。
 * 统计按测试批次或时间范围汇总综合命中率与两路分别命中率，
 * 支撑规划书 3.4 的同资料、同问题、同评价条件对照实验。
 */
@Service
public class KbRetrievalTestService {

    private static final DateTimeFormatter BATCH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KbRetrievalService retrievalService;
    private final KbRetrievalTestMapper testMapper;
    private final KbDocumentMapper documentMapper;
    private final SysParamService sysParamService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public KbRetrievalTestService(KbRetrievalService retrievalService,
                                  KbRetrievalTestMapper testMapper,
                                  KbDocumentMapper documentMapper,
                                  SysParamService sysParamService,
                                  OperationLogService operationLogService,
                                  ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.testMapper = testMapper;
        this.documentMapper = documentMapper;
        this.sysParamService = sysParamService;
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RetrievalTestVO run(RetrievalTestRequest request) {
        int topK = request.getTopK() == null
                ? sysParamService.getInt("kb_recall_topk", 5)
                : request.getTopK();
        if (topK < 1 || topK > 20) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "召回条数需在 1~20 之间");
        }
        String batch = request.getTestBatch() == null || request.getTestBatch().isBlank()
                ? LocalDate.now().format(BATCH_FORMAT)
                : request.getTestBatch();

        RetrievalResult result = retrievalService.search(request.getQuestion(), topK);

        KbRetrievalTest entity = new KbRetrievalTest();
        entity.setQuestion(request.getQuestion());
        entity.setExpectDocId(request.getExpectDocId());
        entity.setExpectChunkId(request.getExpectChunkId());
        entity.setBm25Topk(toJson(result.getBm25()));
        entity.setVectorTopk(toJson(result.getVector()));
        entity.setTestBatch(batch);
        entity.setOperatorId(currentUserId());
        entity.setIsDeleted(0);

        Integer hitRank = null;
        Integer hitRoute = null;
        if (request.getExpectChunkId() != null || request.getExpectDocId() != null) {
            int bm25Rank = rankOf(result.getBm25(), request.getExpectChunkId(), request.getExpectDocId());
            int vectorRank = rankOf(result.getVector(), request.getExpectChunkId(), request.getExpectDocId());
            boolean bm25Hit = bm25Rank > 0;
            boolean vectorHit = vectorRank > 0;
            entity.setHitFlag(bm25Hit || vectorHit ? 1 : 0);
            if (bm25Hit || vectorHit) {
                hitRank = bm25Hit && vectorHit ? Math.min(bm25Rank, vectorRank)
                        : bm25Hit ? bm25Rank : vectorRank;
                hitRoute = bm25Hit && vectorHit ? 3 : bm25Hit ? 1 : 2;
            }
        } else {
            entity.setHitFlag(0);
        }
        entity.setHitRank(hitRank);
        entity.setHitRoute(hitRoute);
        testMapper.insert(entity);

        operationLogService.record("M10", "retrieval-test", "kb_retrieval_test", entity.getId(),
                batch, "问句「" + abbreviate(request.getQuestion()) + "」两路 Top-" + topK
                        + "，命中 " + (entity.getHitFlag() == 1 ? "是" : "否"));
        return toVO(entity, result, request.getExpectDocId());
    }

    public TestStatVO stats(TestStatQuery query) {
        LambdaQueryWrapper<KbRetrievalTest> wrapper = new LambdaQueryWrapper<KbRetrievalTest>()
                .eq(KbRetrievalTest::getIsDeleted, 0)
                .eq(query.getTestBatch() != null && !query.getTestBatch().isBlank(),
                        KbRetrievalTest::getTestBatch, query.getTestBatch())
                .ge(query.getFrom() != null, KbRetrievalTest::getCreatedAt,
                        query.getFrom() == null ? null : query.getFrom().atStartOfDay())
                .le(query.getTo() != null, KbRetrievalTest::getCreatedAt,
                        query.getTo() == null ? null : query.getTo().atTime(23, 59, 59))
                .orderByAsc(KbRetrievalTest::getId);
        List<KbRetrievalTest> rows = testMapper.selectList(wrapper);

        TestStatVO vo = new TestStatVO();
        long bm25Hit = 0;
        long vectorHit = 0;
        long bothHit = 0;
        long rankSum = 0;
        long rankCount = 0;
        for (KbRetrievalTest row : rows) {
            vo.setTotal(vo.getTotal() + 1);
            if (row.getHitFlag() != null && row.getHitFlag() == 1) {
                vo.setHitCount(vo.getHitCount() + 1);
            }
            if (row.getHitRoute() != null) {
                if (row.getHitRoute() == 1) {
                    bm25Hit++;
                } else if (row.getHitRoute() == 2) {
                    vectorHit++;
                } else if (row.getHitRoute() == 3) {
                    bothHit++;
                }
            }
            if (row.getHitRank() != null) {
                rankSum += row.getHitRank();
                rankCount++;
            }
        }
        vo.setBm25Hit(bm25Hit + bothHit);
        vo.setVectorHit(vectorHit + bothHit);
        vo.setBothHit(bothHit);
        vo.setHitRate(rate(vo.getHitCount(), vo.getTotal()));
        vo.setBm25Rate(rate(vo.getBm25Hit(), vo.getTotal()));
        vo.setVectorRate(rate(vo.getVectorHit(), vo.getTotal()));
        vo.setAvgHitRank(rankCount == 0 ? null
                : BigDecimal.valueOf((double) rankSum / rankCount).setScale(2, RoundingMode.HALF_UP));
        return vo;
    }

    /** 期望对象在某一路 Top-K 中的排名（1 起），未命中返回 0 */
    private static int rankOf(List<Hit> hits, Long expectChunkId, Long expectDocId) {
        for (Hit hit : hits) {
            if (expectChunkId != null && expectChunkId.equals(hit.getChunkId())) {
                return hit.getRank();
            }
            if (expectChunkId == null && expectDocId != null && expectDocId.equals(hit.getDocId())) {
                return hit.getRank();
            }
        }
        return 0;
    }

    private RetrievalTestVO toVO(KbRetrievalTest entity, RetrievalResult result, Long expectDocId) {
        RetrievalTestVO vo = new RetrievalTestVO();
        vo.setId(entity.getId());
        vo.setQuestion(entity.getQuestion());
        vo.setExpectDocId(entity.getExpectDocId());
        vo.setExpectChunkId(entity.getExpectChunkId());
        vo.setExpectDocName(expectDocName(entity.getExpectDocId()));
        vo.setBm25(toHitVOs(result.getBm25()));
        vo.setVector(toHitVOs(result.getVector()));
        vo.setTopK(Math.max(result.getBm25().size(), result.getVector().size()));
        vo.setHitFlag(entity.getHitFlag());
        vo.setHitRank(entity.getHitRank());
        vo.setHitRoute(entity.getHitRoute());
        vo.setHitRouteName(routeName(entity.getHitRoute()));
        vo.setTestBatch(entity.getTestBatch());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String expectDocName(Long docId) {
        if (docId == null) {
            return null;
        }
        KbDocument doc = documentMapper.selectById(docId);
        return doc == null ? "（文档已删除）" : doc.getDocName();
    }

    private List<RetrievalHitVO> toHitVOs(List<Hit> hits) {
        List<RetrievalHitVO> list = new ArrayList<>();
        for (Hit hit : hits) {
            RetrievalHitVO vo = new RetrievalHitVO();
            vo.setRank(hit.getRank());
            vo.setChunkId(hit.getChunkId());
            vo.setDocId(hit.getDocId());
            vo.setDocName(hit.getDocName());
            vo.setSectionNo(hit.getSectionNo());
            vo.setSectionTitle(hit.getSectionTitle());
            vo.setScore(hit.getScore());
            vo.setSnippet(hit.getSnippet());
            list.add(vo);
        }
        return list;
    }

    private String toJson(List<Hit> hits) {
        try {
            return objectMapper.writeValueAsString(toHitVOs(hits));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String routeName(Integer route) {
        if (route == null) {
            return null;
        }
        return switch (route) {
            case 1 -> "稀疏命中";
            case 2 -> "向量命中";
            case 3 -> "两路均命中";
            default -> null;
        };
    }

    private static BigDecimal rate(long hit, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf((double) hit / total).setScale(4, RoundingMode.HALF_UP);
    }

    private static String abbreviate(String text) {
        return text.length() <= 30 ? text : text.substring(0, 30) + "…";
    }

    private Long currentUserId() {
        CurrentUser user = AuthContext.get();
        return user == null ? null : user.getUserId();
    }
}
