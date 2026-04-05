package com.hify.workflow.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.workflow.dto.*;
import com.hify.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public Result<WorkflowDetailVO> create(@RequestBody @Valid WorkflowCreateRequest request) {
        return Result.ok(workflowService.create(request));
    }

    @GetMapping
    public Result<PageResult<WorkflowListItem>> list(WorkflowQueryRequest request) {
        return workflowService.list(request);
    }

    @GetMapping("/{id}")
    public Result<WorkflowDetailVO> getDetail(@PathVariable Long id) {
        return Result.ok(workflowService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<WorkflowDetailVO> update(@PathVariable Long id,
                                           @RequestBody @Valid WorkflowUpdateRequest request) {
        return Result.ok(workflowService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }
}
