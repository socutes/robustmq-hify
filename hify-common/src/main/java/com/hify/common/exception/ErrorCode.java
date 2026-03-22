package com.hify.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 1000-1999 通用
    PARAM_ERROR(1000, "请求参数错误"),
    RESOURCE_NOT_FOUND(1001, "资源不存在"),
    UNAUTHORIZED(1002, "未授权"),
    FORBIDDEN(1003, "无权限"),
    SYSTEM_ERROR(1999, "系统内部错误"),

    // 2000-2999 Provider
    PROVIDER_NOT_FOUND(2000, "模型提供商不存在"),
    PROVIDER_NAME_DUPLICATE(2001, "提供商名称已存在"),
    PROVIDER_DISABLED(2002, "模型提供商已禁用"),
    PROVIDER_CONNECTION_FAILED(2003, "提供商连通性测试失败"),

    // 3000-3999 Agent
    AGENT_NOT_FOUND(3000, "Agent 不存在"),
    AGENT_NAME_DUPLICATE(3001, "Agent 名称已存在"),
    AGENT_DISABLED(3002, "Agent 已禁用"),

    // 4000-4999 Chat
    SESSION_NOT_FOUND(4000, "对话会话不存在"),
    MESSAGE_NOT_FOUND(4001, "消息不存在"),
    CHAT_TIMEOUT(4002, "对话请求超时"),
    CHAT_STREAM_ERROR(4003, "流式响应异常"),

    // 5000-5999 MCP
    MCP_SERVER_NOT_FOUND(5000, "MCP Server 不存在"),
    MCP_TOOL_CALL_FAILED(5001, "MCP 工具调用失败"),

    // 6000-6999 Workflow
    WORKFLOW_NOT_FOUND(6000, "工作流不存在"),
    WORKFLOW_EXECUTE_FAILED(6001, "工作流执行失败"),

    // 7000-7999 Knowledge
    KNOWLEDGE_BASE_NOT_FOUND(7000, "知识库不存在"),
    DOCUMENT_NOT_FOUND(7001, "文档不存在"),
    DOCUMENT_PARSE_FAILED(7002, "文档解析失败");

    private final int code;
    private final String message;
}
