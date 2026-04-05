package com.hify.workflow.service;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.workflow.dto.*;

public interface WorkflowService {

    WorkflowDetailVO create(WorkflowCreateRequest request);

    Result<PageResult<WorkflowListItem>> list(WorkflowQueryRequest request);

    WorkflowDetailVO getDetail(Long id);

    WorkflowDetailVO update(Long id, WorkflowUpdateRequest request);

    void delete(Long id);
}
