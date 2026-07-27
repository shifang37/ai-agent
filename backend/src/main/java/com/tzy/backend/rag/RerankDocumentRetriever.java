package com.tzy.backend.rag;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 两阶段检索器：向量粗召回 + Rerank 精排
 * <p>
 * 第一阶段由底层 DocumentRetriever（向量检索，topK 放宽到 15-20）做粗召回，
 * 第二阶段用 DashScope gte-rerank 交叉编码器模型对候选文档精排，取 topN 送入上下文。
 * 相比纯双塔向量相似度，交叉编码器让 query 与文档做全交互注意力打分，精度更高。
 *
 * @author shifang37
 */
@Slf4j
public class RerankDocumentRetriever implements DocumentRetriever {

    /**
     * DashScope 重排模型名（注意：旧版 gte-rerank 已下线，调用会返回 403 AccessDenied）
     */
    private static final String RERANK_MODEL = "gte-rerank-v2";

    /**
     * 第一阶段：向量粗召回检索器
     */
    private final DocumentRetriever recallRetriever;

    /**
     * 第二阶段：重排模型（DashScope gte-rerank）
     */
    private final RerankModel rerankModel;

    /**
     * 精排后保留的文档数量
     */
    private final int rerankTopN;

    public RerankDocumentRetriever(DocumentRetriever recallRetriever, RerankModel rerankModel, int rerankTopN) {
        this.recallRetriever = recallRetriever;
        this.rerankModel = rerankModel;
        this.rerankTopN = rerankTopN;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 第一阶段：向量粗召回
        List<Document> candidates = recallRetriever.retrieve(query);
        log.info("[两阶段检索] 粗召回 {} 篇候选文档", candidates.size());
        if (candidates.isEmpty() || candidates.size() <= rerankTopN) {
            return candidates;
        }
        // 第二阶段：Rerank 精排
        try {
            DashScopeRerankOptions options = DashScopeRerankOptions.builder()
                    .withModel(RERANK_MODEL)
                    .withTopN(rerankTopN)
                    .build();
            RerankResponse response = rerankModel.call(new RerankRequest(query.text(), candidates, options));
            List<DocumentWithScore> results = response.getResults();
            String scoreInfo = results.stream()
                    .map(r -> String.format("%.4f", r.getScore()))
                    .collect(Collectors.joining(", "));
            log.info("[两阶段检索] 精排后保留 top{}，得分: [{}]", results.size(), scoreInfo);
            return results.stream()
                    .map(DocumentWithScore::getOutput)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // 精排失败降级为向量召回结果，保证可用性
            log.warn("[两阶段检索] Rerank 精排失败，降级返回向量召回 top{}: {}", rerankTopN, e.getMessage());
            return candidates.subList(0, rerankTopN);
        }
    }
}
