package com.hify.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.dto.*;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.service.McpService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private final McpServerMapper mcpServerMapper;

    // ── CRUD ────────────────────────────────────────────────────

    @Override
    public McpServerVO create(McpServerCreateRequest request) {
        checkNameUnique(request.getName(), null);
        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setEndpoint(request.getEndpoint());
        server.setDescription(request.getDescription() != null ? request.getDescription() : "");
        server.setEnabled(1);
        mcpServerMapper.insert(server);
        return McpServerVO.from(server);
    }

    @Override
    public Result<PageResult<McpServerVO>> list(McpQueryRequest request) {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
                .eq(request.getEnabled() != null, McpServer::getEnabled, request.getEnabled())
                .orderByDesc(McpServer::getCreatedAt);
        int size = Math.min(request.getPageSize(), 100);
        var p = mcpServerMapper.selectPage(new Page<>(request.getPage(), size), wrapper);
        List<McpServerVO> items = p.getRecords().stream().map(McpServerVO::from).collect(Collectors.toList());
        return PageResult.of(items, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    @Override
    public McpServerVO getDetail(Long id) {
        return McpServerVO.from(getOrThrow(id));
    }

    @Override
    public McpServerVO update(Long id, McpServerUpdateRequest request) {
        McpServer server = getOrThrow(id);
        checkNameUnique(request.getName(), id);
        server.setName(request.getName());
        server.setEndpoint(request.getEndpoint());
        if (request.getDescription() != null) server.setDescription(request.getDescription());
        if (request.getEnabled() != null) server.setEnabled(request.getEnabled());
        mcpServerMapper.updateById(server);
        return McpServerVO.from(server);
    }

    @Override
    public void delete(Long id) {
        getOrThrow(id);
        mcpServerMapper.deleteById(id);
    }

    // ── 连通性测试 ────────────────────────────────────────────────

    @Override
    public McpTestResult testConnection(Long id) {
        McpServer server = getOrThrow(id);
        long start = System.currentTimeMillis();
        try {
            List<String> tools = listToolsFromEndpoint(server.getEndpoint());
            int latencyMs = (int) (System.currentTimeMillis() - start);
            log.info("MCP 连通测试成功 server={} tools={} latency={}ms", server.getName(), tools.size(), latencyMs);
            return McpTestResult.ok(latencyMs, tools);
        } catch (Exception e) {
            log.warn("MCP 连通测试失败 server={}: {}", server.getName(), e.getMessage());
            return McpTestResult.fail(e.getMessage());
        }
    }

    // ── 工具调用 ─────────────────────────────────────────────────

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServer server = getOrThrow(mcpServerId);
        log.info("MCP callTool server={} tool={} args={}", server.getName(), toolName, arguments);

        var transport = HttpClientSseClientTransport.builder(server.getEndpoint()).build();
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .build()) {

            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, arguments));

            return result.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.error("MCP callTool failed server={} tool={}: {}", server.getName(), toolName, e.getMessage());
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "工具调用失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        McpServer server = getOrThrow(mcpServerId);
        return listToolsFromEndpoint(server.getEndpoint());
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    @Override
    public List<McpToolDetail> listToolsDetail(Long mcpServerId) {
        McpServer server = getOrThrow(mcpServerId);
        var transport = HttpClientSseClientTransport.builder(server.getEndpoint()).build();
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();
            return client.listTools().tools().stream()
                    .map(this::toToolDetail)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "无法连接 MCP Server: " + e.getMessage());
        }
    }

    @Override
    public McpDebugResult debugTool(Long mcpServerId, McpDebugRequest request) {
        long start = System.currentTimeMillis();
        try {
            String result = callTool(mcpServerId, request.getToolName(),
                    request.getArguments() != null ? request.getArguments() : java.util.Collections.emptyMap());
            int elapsedMs = (int) (System.currentTimeMillis() - start);
            return McpDebugResult.ok(result, elapsedMs);
        } catch (BizException e) {
            int elapsedMs = (int) (System.currentTimeMillis() - start);
            return McpDebugResult.fail(e.getMessage(), elapsedMs);
        } catch (Exception e) {
            int elapsedMs = (int) (System.currentTimeMillis() - start);
            return McpDebugResult.fail(e.getMessage(), elapsedMs);
        }
    }

    @SuppressWarnings("unchecked")
    private McpToolDetail toToolDetail(McpSchema.Tool tool) {
        McpToolDetail detail = new McpToolDetail();
        detail.setName(tool.name());
        detail.setDescription(tool.description());
        // inputSchema is McpSchema.JsonSchema — convert to Map via its properties
        if (tool.inputSchema() != null) {
            java.util.LinkedHashMap<String, Object> schema = new java.util.LinkedHashMap<>();
            schema.put("type", "object");
            if (tool.inputSchema().properties() != null) {
                schema.put("properties", tool.inputSchema().properties());
            }
            if (tool.inputSchema().required() != null) {
                schema.put("required", tool.inputSchema().required());
                detail.setRequiredParams(tool.inputSchema().required());
            }
            detail.setInputSchema(schema);
        }
        return detail;
    }

    private List<String> listToolsFromEndpoint(String endpoint) {
        var transport = HttpClientSseClientTransport.builder(endpoint).build();
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();
            return client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("无法连接 MCP Server: " + e.getMessage(), e);
        }
    }

    private McpServer getOrThrow(Long id) {
        McpServer server = mcpServerMapper.selectById(id);
        if (server == null) throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
        return server;
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getName, name)
                .ne(excludeId != null, McpServer::getId, excludeId);
        if (mcpServerMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server 名称已存在");
        }
    }
}
