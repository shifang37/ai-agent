package com.tzy.backend.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * @author shifang37
 */
@Configuration
@org.springframework.context.annotation.Profile("pgvector")
@Slf4j
public class PgVectorVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1536)                    // Optional: defaults to model dimensions or 1536
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(true)              // Optional: defaults to false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
        // 仅在向量表为空时加载文档，避免每次重启重复写入、重复消耗 embedding 额度
        long existingCount = countExistingVectors(jdbcTemplate);
        if (existingCount > 0) {
            log.info("向量表 vector_store 已有 {} 条数据，跳过文档加载", existingCount);
            return vectorStore;
        }
        // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        vectorStore.add(documents);
        log.info("已向向量表 vector_store 加载 {} 篇文档", documents.size());
        return vectorStore;
    }

    /**
     * 统计向量表现有数据量；表尚不存在（首次启动，schema 稍后由 PgVectorStore 初始化）时按 0 处理
     */
    private long countExistingVectors(JdbcTemplate jdbcTemplate) {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM public.vector_store", Long.class);
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            return 0;
        }
    }
}


