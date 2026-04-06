package com.hify.mcp.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.mcp.dto.*;
import com.hify.mcp.service.McpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @PostMapping
    public Result<McpServerVO> create(@Valid @RequestBody McpServerCreateRequest request) {
        return Result.ok(mcpService.create(request));
    }

    @GetMapping
    public Result<PageResult<McpServerVO>> list(McpQueryRequest request) {
        return mcpService.list(request);
    }

    @GetMapping("/{id}")
    public Result<McpServerVO> detail(@PathVariable Long id) {
        return Result.ok(mcpService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<McpServerVO> update(@PathVariable Long id,
                                      @Valid @RequestBody McpServerUpdateRequest request) {
        return Result.ok(mcpService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    public Result<McpTestResult> test(@PathVariable Long id) {
        return Result.ok(mcpService.testConnection(id));
    }

    @GetMapping("/{id}/tools")
    public Result<java.util.List<com.hify.mcp.dto.McpToolDetail>> tools(@PathVariable Long id) {
        return Result.ok(mcpService.listToolsDetail(id));
    }

    @PostMapping("/{id}/debug")
    public Result<com.hify.mcp.dto.McpDebugResult> debug(@PathVariable Long id,
                                                           @Valid @RequestBody McpDebugRequest request) {
        return Result.ok(mcpService.debugTool(id, request));
    }
}
