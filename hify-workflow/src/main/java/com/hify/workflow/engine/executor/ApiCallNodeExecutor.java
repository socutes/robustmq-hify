package com.hify.workflow.engine.executor;

import com.hify.workflow.dto.NodeConfigDef;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeExecutor;
import com.hify.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ApiCallNodeExecutor implements NodeExecutor {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public String nodeType() {
        return "API_CALL";
    }

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        NodeConfigDef.ApiCallConfig apiConfig = (NodeConfigDef.ApiCallConfig) config;

        String url = ctx.resolve(apiConfig.url() != null ? apiConfig.url() : "");
        String method = apiConfig.method() != null ? apiConfig.method().toUpperCase() : "GET";
        String outputVar = apiConfig.outputVariable() != null ? apiConfig.outputVariable() : "response";

        log.info("ApiCallNodeExecutor node={} {} {}", node.getNodeKey(), method, url);

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .method(method, null)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                ctx.set(node.getNodeKey(), outputVar, body);
            }
        } catch (Exception e) {
            log.error("ApiCallNodeExecutor failed node={}: {}", node.getNodeKey(), e.getMessage());
            ctx.set(node.getNodeKey(), outputVar, "API 调用失败: " + e.getMessage());
        }
    }
}
