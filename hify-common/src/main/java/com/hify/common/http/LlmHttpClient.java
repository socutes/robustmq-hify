package com.hify.common.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class LlmHttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final OkHttpClient streamClient;

    public LlmHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        // 流式读取需要更长的 read timeout，设为 0 表示不超时（由业务层控制）
        this.streamClient = httpClient.newBuilder()
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 同步 POST，返回响应体字符串。
     * 连接超时 5s，读超时 120s。
     */
    public String post(String url, Map<String, String> headers, String body) {
        Request request = buildRequest(url, headers, body);
        long start = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM POST {} status={} cost={}ms", url, response.code(), elapsed);

            checkStatus(response.code(), url);

            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : "";
        } catch (SocketTimeoutException e) {
            log.warn("LLM POST {} timeout after {}ms", url, System.currentTimeMillis() - start);
            throw new LlmApiException(LlmApiException.Type.TIMEOUT, "LLM 请求超时：" + url, e);
        } catch (LlmApiException e) {
            throw e;
        } catch (IOException e) {
            log.error("LLM POST {} error: {}", url, e.getMessage());
            throw new LlmApiException(LlmApiException.Type.UNKNOWN, "LLM 请求异常：" + e.getMessage(), e);
        }
    }

    /**
     * 流式 POST，按行回调。
     * 每收到一行非空内容调用 callback，结束后调用 callback(null) 表示流结束。
     */
    public void stream(String url, Map<String, String> headers, String body, Consumer<String> callback) {
        Request request = buildRequest(url, headers, body);
        long start = System.currentTimeMillis();

        try (Response response = streamClient.newCall(request).execute()) {
            log.info("LLM STREAM {} status={}", url, response.code());

            checkStatus(response.code(), url);

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                callback.accept(null);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody.byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        callback.accept(line);
                    }
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM STREAM {} completed cost={}ms", url, elapsed);
            callback.accept(null);

        } catch (SocketTimeoutException e) {
            log.warn("LLM STREAM {} timeout", url);
            throw new LlmApiException(LlmApiException.Type.TIMEOUT, "LLM 流式请求超时：" + url, e);
        } catch (LlmApiException e) {
            throw e;
        } catch (IOException e) {
            log.error("LLM STREAM {} error: {}", url, e.getMessage());
            throw new LlmApiException(LlmApiException.Type.UNKNOWN, "LLM 流式请求异常：" + e.getMessage(), e);
        }
    }

    // ── 私有工具方法 ──────────────────────────────────────────

    private Request buildRequest(String url, Map<String, String> headers, String body) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return builder.build();
    }

    private void checkStatus(int code, String url) {
        if (code == 200) return;
        if (code == 401 || code == 403) {
            log.warn("LLM {} auth failed status={}", url, code);
            throw new LlmApiException(LlmApiException.Type.AUTH_FAILED, code, "API Key 无效或无权限");
        }
        if (code == 429) {
            log.warn("LLM {} rate limited", url);
            throw new LlmApiException(LlmApiException.Type.RATE_LIMITED, code, "请求速率超限，请稍后重试");
        }
        throw new LlmApiException(LlmApiException.Type.UNKNOWN, code, "LLM 请求失败，status=" + code);
    }
}
