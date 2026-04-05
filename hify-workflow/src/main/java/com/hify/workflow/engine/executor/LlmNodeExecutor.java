package com.hify.workflow.engine.executor;

import com.hify.provider.adapter.ProviderAdapterFactory;
import com.hify.provider.dto.ChatMessage;
import com.hify.provider.dto.ChatRequest;
import com.hify.provider.dto.ChatResponse;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ModelConfigMapper;
import com.hify.provider.mapper.ProviderMapper;
import com.hify.workflow.dto.NodeConfigDef;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeExecutor;
import com.hify.workflow.entity.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory adapterFactory;

    @Override
    public String nodeType() {
        return "LLM";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        NodeConfigDef.LlmConfig llmConfig = (NodeConfigDef.LlmConfig) config;

        // 解析模板变量
        String resolvedPrompt = ctx.resolve(llmConfig.prompt() != null ? llmConfig.prompt() : "");
        String userMessage = ctx.resolve("{{start.userMessage}}");
        String outputVar = llmConfig.outputVariable() != null ? llmConfig.outputVariable() : "output";

        // 加载模型配置（优先用节点指定的，否则找第一个可用的）
        ModelConfig modelConfig = loadModelConfig(llmConfig.modelConfigId());
        Provider provider = providerMapper.selectById(modelConfig.getProviderId());
        if (provider == null || provider.getEnabled() != 1) {
            throw new IllegalStateException("节点 [" + node.getNodeKey() + "] 提供商不可用");
        }

        // 构造消息
        ChatMessage systemMsg = new ChatMessage();
        systemMsg.setRole("system");
        systemMsg.setContent(resolvedPrompt.isBlank() ? "你是一个有帮助的助手。" : resolvedPrompt);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setRole("user");
        userMsg.setContent(userMessage);

        ChatRequest chatRequest = ChatRequest.builder()
                .modelId(modelConfig.getModelId())
                .messages(List.of(systemMsg, userMsg))
                .temperature(null)
                .maxTokens(null)
                .build();

        log.info("LlmNodeExecutor node={} prompt={}...", node.getNodeKey(),
                resolvedPrompt.length() > 30 ? resolvedPrompt.substring(0, 30) : resolvedPrompt);

        ChatResponse response = adapterFactory.get(provider.getType()).chat(provider, chatRequest);
        ctx.set(node.getNodeKey(), outputVar, response.getContent());
    }

    private ModelConfig loadModelConfig(Long modelConfigId) {
        if (modelConfigId != null) {
            ModelConfig mc = modelConfigMapper.selectById(modelConfigId);
            if (mc != null && mc.getEnabled() == 1) return mc;
        }
        // fallback：取第一个可用的模型配置
        ModelConfig mc = modelConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getEnabled, 1)
                        .last("LIMIT 1")
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("没有可用的模型配置"));
        return mc;
    }
}
