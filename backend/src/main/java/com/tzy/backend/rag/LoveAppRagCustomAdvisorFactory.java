package com.tzy.backend.rag;

import com.alibaba.cloud.ai.model.RerankModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * @author shifang37
 */
@Slf4j
public class LoveAppRagCustomAdvisorFactory {

    /**
     * 粗召回候选文档数（放宽 topK，交给 rerank 精排）
     */
    private static final int RECALL_TOP_K = 15;

    /**
     * 精排后送入上下文的文档数
     */
    private static final int RERANK_TOP_N = 3;

    /**
     * 单阶段检索（仅向量召回），保留兼容旧调用
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status) {
        return createLoveAppRagCustomAdvisor(vectorStore, status, null);
    }

    /**
     * 两阶段检索：向量粗召回（topK=15、放宽相似度阈值）+ gte-rerank 精排取 top3
     *
     * @param rerankModel 重排模型，传 null 时退化为单阶段向量检索（topK=3）
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status, RerankModel rerankModel) {
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        DocumentRetriever documentRetriever;
        if (rerankModel == null) {
            documentRetriever = VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .filterExpression(expression) // 过滤条件
                    .similarityThreshold(0.5) // 相似度阈值
                    .topK(RERANK_TOP_N) // 返回文档数量
                    .build();
        } else {
            // 粗召回：topK 放宽、阈值降低，保证召回率；精度交给第二阶段 rerank
            DocumentRetriever recallRetriever = VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .filterExpression(expression)
                    .similarityThreshold(0.3)
                    .topK(RECALL_TOP_K)
                    .build();
            documentRetriever = new RerankDocumentRetriever(recallRetriever, rerankModel, RERANK_TOP_N);
        }
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(LoveAppContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
