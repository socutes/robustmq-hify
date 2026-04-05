package com.hify.workflow.engine.executor;

import com.hify.knowledge.dto.ChunkVO;
import com.hify.knowledge.service.KnowledgeService;
import com.hify.workflow.dto.NodeConfigDef;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeExecutor;
import com.hify.workflow.entity.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeNodeExecutor implements NodeExecutor {

    private final KnowledgeService knowledgeService;

    @Override
    public String nodeType() {
        return "KNOWLEDGE";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        NodeConfigDef.KnowledgeConfig kbConfig = (NodeConfigDef.KnowledgeConfig) config;

        if (kbConfig.knowledgeBaseId() == null) {
            throw new IllegalArgumentException("知识库节点 [" + node.getNodeKey() + "] 未配置 knowledgeBaseId");
        }

        String query = ctx.resolve(kbConfig.query() != null ? kbConfig.query() : "{{start.userMessage}}");
        int topK = kbConfig.topK() != null ? kbConfig.topK() : 3;
        String outputVar = kbConfig.outputVariable() != null ? kbConfig.outputVariable() : "docs";

        List<ChunkVO> chunks = knowledgeService.searchChunks(kbConfig.knowledgeBaseId(), query, topK);

        // 拼成字符串，供后续 LLM 节点通过模板变量引用
        String docs = chunks.stream()
                .map(c -> "[" + (chunks.indexOf(c) + 1) + "] " + c.getContent())
                .collect(Collectors.joining("\n"));

        ctx.set(node.getNodeKey(), outputVar, docs);
        log.info("KnowledgeNodeExecutor node={} kbId={} query='{}' hits={}",
                node.getNodeKey(), kbConfig.knowledgeBaseId(), query, chunks.size());
    }
}
