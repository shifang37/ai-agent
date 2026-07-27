package com.tzy.backend.rag;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 两阶段检索评测：对比「单阶段向量检索 top3」与「向量粗召回 top15 + gte-rerank 精排 top3」
 * 的 Hit Rate@3 / Hit Rate@1
 * <p>
 * 评测集：src/test/resources/rag-eval-dataset.json，45 条口语化改写问句，
 * 每条以知识块中唯一的课程名作为命中标记（检出文档文本包含该标记即命中）。
 * <p>
 * 运行：mvn test -Dtest=RetrievalEvalTest（需 application-local.yml 或环境变量提供 DashScope API Key）
 *
 * @author shifang37
 */
public class RetrievalEvalTest {

    @Test
    public void evalHitRate() throws Exception {
        String apiKey = resolveApiKey();
        DashScopeApi dashScopeApi = new DashScopeApi(apiKey);
        DashScopeEmbeddingModel embeddingModel = new DashScopeEmbeddingModel(dashScopeApi);
        DashScopeRerankModel rerankModel = new DashScopeRerankModel(dashScopeApi);

        // 构建向量库（与生产相同的 Markdown 分块方式）
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = loadMarkdowns();
        vectorStore.add(documents);
        System.out.println("知识库分块数: " + documents.size());

        // A 基线：单阶段向量检索（课程原始配置 topK=3, threshold=0.5）
        DocumentRetriever baseline = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(3)
                .build();
        // 粗召回：topK=15, threshold=0.3
        DocumentRetriever recall = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.3)
                .topK(15)
                .build();
        // B 消融组：仅放宽召回，取向量序 top3（不做 rerank，用于剥离阈值放宽的贡献）
        DocumentRetriever recallOnly = query -> {
            List<Document> docs = recall.retrieve(query);
            return docs.subList(0, Math.min(3, docs.size()));
        };
        // C 两阶段：粗召回 + gte-rerank-v2 精排 top3
        DocumentRetriever twoStage = new RerankDocumentRetriever(recall, rerankModel, 3);

        // 加载评测集
        JSONArray dataset = JSONUtil.parseArray(ResourceUtil.readUtf8Str("rag-eval-dataset.json"));
        int total = 0;
        int baseHit3 = 0, baseHit1 = 0, ablHit3 = 0, ablHit1 = 0, twoHit3 = 0, twoHit1 = 0;
        List<String> baselineMisses = new ArrayList<>();
        List<String> ablationMisses = new ArrayList<>();
        List<String> twoStageMisses = new ArrayList<>();

        for (Object entryObj : dataset) {
            JSONObject entry = (JSONObject) entryObj;
            String marker = entry.getStr("marker");
            for (Object queryObj : entry.getJSONArray("queries")) {
                String queryText = queryObj.toString();
                total++;

                List<Document> baseDocs = baseline.retrieve(new Query(queryText));
                if (hit(baseDocs, marker, 3)) baseHit3++;
                else baselineMisses.add(queryText + " -> " + marker);
                if (hit(baseDocs, marker, 1)) baseHit1++;

                List<Document> ablDocs = recallOnly.retrieve(new Query(queryText));
                if (hit(ablDocs, marker, 3)) ablHit3++;
                else ablationMisses.add(queryText + " -> " + marker);
                if (hit(ablDocs, marker, 1)) ablHit1++;

                List<Document> twoDocs = twoStage.retrieve(new Query(queryText));
                if (hit(twoDocs, marker, 3)) twoHit3++;
                else twoStageMisses.add(queryText + " -> " + marker);
                if (hit(twoDocs, marker, 1)) twoHit1++;

                System.out.printf("[%02d] %s | 基线@3:%s 仅放宽召回@3:%s 两阶段@3:%s%n",
                        total, queryText, hit(baseDocs, marker, 3) ? "✓" : "✗",
                        hit(ablDocs, marker, 3) ? "✓" : "✗", hit(twoDocs, marker, 3) ? "✓" : "✗");
            }
        }

        System.out.println("\n========== 评测结果 (" + total + " 条查询) ==========");
        System.out.printf("A 单阶段向量检索(topK3,阈值0.5)   Hit@1: %d/%d = %.1f%%   Hit@3: %d/%d = %.1f%%%n",
                baseHit1, total, 100.0 * baseHit1 / total, baseHit3, total, 100.0 * baseHit3 / total);
        System.out.printf("B 仅放宽召回(topK15,阈值0.3,取前3) Hit@1: %d/%d = %.1f%%   Hit@3: %d/%d = %.1f%%%n",
                ablHit1, total, 100.0 * ablHit1 / total, ablHit3, total, 100.0 * ablHit3 / total);
        System.out.printf("C 两阶段(召回+gte-rerank-v2精排)   Hit@1: %d/%d = %.1f%%   Hit@3: %d/%d = %.1f%%%n",
                twoHit1, total, 100.0 * twoHit1 / total, twoHit3, total, 100.0 * twoHit3 / total);
        System.out.println("\nA 基线未命中@3 (" + baselineMisses.size() + "):");
        baselineMisses.forEach(m -> System.out.println("  " + m));
        System.out.println("B 仅放宽召回未命中@3 (" + ablationMisses.size() + "):");
        ablationMisses.forEach(m -> System.out.println("  " + m));
        System.out.println("C 两阶段未命中@3 (" + twoStageMisses.size() + "):");
        twoStageMisses.forEach(m -> System.out.println("  " + m));
    }

    private boolean hit(List<Document> docs, String marker, int topN) {
        return docs.stream().limit(topN).anyMatch(doc -> doc.getText() != null && doc.getText().contains(marker));
    }

    private List<Document> loadMarkdowns() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:document/*.md");
        List<Document> allDocuments = new ArrayList<>();
        for (Resource resource : resources) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .withAdditionalMetadata("filename", String.valueOf(resource.getFilename()))
                    .build();
            allDocuments.addAll(new MarkdownDocumentReader(resource, config).get());
        }
        return allDocuments;
    }

    private String resolveApiKey() {
        String env = System.getenv("AI_DASHSCOPE_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        // 从 application-local.yml 中解析 api-key
        String yml = ResourceUtil.readUtf8Str("application-local.yml");
        Matcher matcher = Pattern.compile("api-key:\\s*(sk-\\w+)").matcher(yml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("未找到 DashScope API Key（环境变量 AI_DASHSCOPE_API_KEY 或 application-local.yml）");
    }
}
