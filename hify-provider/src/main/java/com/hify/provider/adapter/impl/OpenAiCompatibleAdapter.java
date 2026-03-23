package com.hify.provider.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI-Compatible 供应商（如 DeepSeek、Moonshot 等）。
 * 与 OpenAI 接口完全兼容，直接复用父类逻辑。
 */
@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    @Override
    public List<String> supportedTypes() {
        return List.of("OPENAI_COMPATIBLE", "DEEPSEEK");
    }
}
