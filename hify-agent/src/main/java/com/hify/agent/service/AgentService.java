package com.hify.agent.service;

import com.hify.agent.dto.*;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;

public interface AgentService {

    AgentDetailResponse create(AgentCreateRequest request);

    AgentDetailResponse update(Long id, AgentUpdateRequest request);

    void bindTools(Long id, AgentToolBindRequest request);

    void delete(Long id);

    AgentDetailResponse getDetail(Long id);

    Result<PageResult<AgentListItem>> list(AgentQueryRequest request);
}
