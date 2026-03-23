package com.hify.provider.adapter;

import com.hify.provider.dto.ChatRequest;
import com.hify.provider.dto.ChatResponse;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.function.Consumer;

public interface ProviderAdapter {

    /** 该 Adapter 支持的供应商类型（大写），可多个 */
    List<String> supportedTypes();

    /** 连通性测试 */
    ConnectionTestResult testConnection(Provider provider, OkHttpClient testClient);

    /** 拉取模型列表，返回模型 ID 列表 */
    List<String> listModels(Provider provider, OkHttpClient client);

    /**
     * 同步对话，返回完整回复。
     * 适用于非流式场景（工作流节点、批处理）。
     */
    ChatResponse chat(Provider provider, ChatRequest request);

    /**
     * 流式对话，逐 chunk 回调。
     * onChunk 每收到一段 delta 文本触发一次。
     * 流结束后返回完整的 ChatResponse（含 finishReason 和 token 统计）。
     */
    ChatResponse streamChat(Provider provider, ChatRequest request, Consumer<String> onChunk);
}
