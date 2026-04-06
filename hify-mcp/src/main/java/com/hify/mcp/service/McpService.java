package com.hify.mcp.service;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.mcp.dto.*;

import java.util.List;
import java.util.Map;

public interface McpService {

    McpServerVO create(McpServerCreateRequest request);

    Result<PageResult<McpServerVO>> list(McpQueryRequest request);

    McpServerVO getDetail(Long id);

    McpServerVO update(Long id, McpServerUpdateRequest request);

    void delete(Long id);

    McpTestResult testConnection(Long id);

    /** 调用指定 Server 的工具，返回文本结果 */
    String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments);

    /** 列出指定 Server 的所有工具名 */
    List<String> listTools(Long mcpServerId);

    /** 列出工具详情（含 description 和 inputSchema） */
    List<McpToolDetail> listToolsDetail(Long mcpServerId);

    /** 调试调用工具，返回结果和耗时 */
    McpDebugResult debugTool(Long mcpServerId, McpDebugRequest request);
}
