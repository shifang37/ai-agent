package com.tzy.backend.rag;

import com.tzy.backend.constant.FileConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;

@Configuration
@Slf4j
public class LoveAppVectorStoreConfig {

    /**
     * 向量库本地缓存文件：首次构建（关键词增强 + 向量化）后落盘，
     * 之后启动直接加载，不再重复调用 AI；文档内容变更后删除该文件即可重建
     */
    private static final File VECTOR_STORE_CACHE_FILE =
            new File(FileConstant.FILE_SAVE_DIR, "love-app-vector-store.json");

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
        // 命中缓存：直接恢复向量数据，跳过关键词增强和向量化
        if (VECTOR_STORE_CACHE_FILE.exists()) {
            simpleVectorStore.load(VECTOR_STORE_CACHE_FILE);
            log.info("从本地缓存加载恋爱大师向量库：{}", VECTOR_STORE_CACHE_FILE.getAbsolutePath());
            return simpleVectorStore;
        }
        // 加载文档
        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        //List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documents);
        List<Document> enrichDocuments = myKeywordEnricher.enrichDocuments(documents);
        simpleVectorStore.add(enrichDocuments);
        // 落盘缓存，供下次启动直接加载
        VECTOR_STORE_CACHE_FILE.getParentFile().mkdirs();
        simpleVectorStore.save(VECTOR_STORE_CACHE_FILE);
        log.info("恋爱大师向量库构建完成并已缓存到：{}", VECTOR_STORE_CACHE_FILE.getAbsolutePath());
        return simpleVectorStore;
    }
}
