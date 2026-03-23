package com.hify.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.agent.dto.*;
import com.hify.agent.entity.Agent;
import com.hify.agent.entity.AgentTool;
import com.hify.agent.mapper.AgentMapper;
import com.hify.agent.mapper.AgentToolMapper;
import com.hify.agent.service.AgentService;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentToolMapper agentToolMapper;
    private final ProviderService providerService;

    // ── 创建 ────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(cacheNames = "agent-cache", key = "'list'")
    public AgentDetailResponse create(AgentCreateRequest request) {
        checkNameUnique(request.getName(), null);
        providerService.getEnabledModelConfigOrThrow(request.getModelConfigId());

        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setDescription(request.getDescription() != null ? request.getDescription() : "");
        agent.setSystemPrompt(request.getSystemPrompt() != null ? request.getSystemPrompt() : "");
        agent.setModelConfigId(request.getModelConfigId());
        agent.setTemperature(request.getTemperature());
        agent.setMaxTokens(request.getMaxTokens());
        agent.setMaxContextTurns(request.getMaxContextTurns());
        agent.setEnabled(1);
        agentMapper.insert(agent);

        List<Long> toolIds = request.getToolIds();
        if (!CollectionUtils.isEmpty(toolIds)) {
            batchInsertTools(agent.getId(), toolIds);
        }

        return AgentDetailResponse.from(agent, toolIds != null ? toolIds : List.of());
    }

    // ── 更新基本信息 ─────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = "agent-cache", key = "#id"),
        @CacheEvict(cacheNames = "agent-cache", key = "'list'")
    })
    public AgentDetailResponse update(Long id, AgentUpdateRequest request) {
        Agent agent = getOrThrow(id);
        checkNameUnique(request.getName(), id);
        providerService.getEnabledModelConfigOrThrow(request.getModelConfigId());

        agent.setName(request.getName());
        if (request.getDescription() != null) agent.setDescription(request.getDescription());
        if (request.getSystemPrompt() != null) agent.setSystemPrompt(request.getSystemPrompt());
        agent.setModelConfigId(request.getModelConfigId());
        if (request.getTemperature() != null) agent.setTemperature(request.getTemperature());
        if (request.getMaxTokens() != null) agent.setMaxTokens(request.getMaxTokens());
        if (request.getMaxContextTurns() != null) agent.setMaxContextTurns(request.getMaxContextTurns());
        agentMapper.updateById(agent);

        List<Long> toolIds = agentToolMapper.selectMcpServerIdsByAgentId(id);
        return AgentDetailResponse.from(agent, toolIds);
    }

    // ── 更新工具列表（全量替换）────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = "agent-cache", key = "#id"),
        @CacheEvict(cacheNames = "agent-cache", key = "'list'")
    })
    public void bindTools(Long id, AgentToolBindRequest request) {
        getOrThrow(id);
        agentToolMapper.deleteByAgentId(id);
        if (!CollectionUtils.isEmpty(request.getToolIds())) {
            batchInsertTools(id, request.getToolIds());
        }
    }

    // ── 删除 ────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = "agent-cache", key = "#id"),
        @CacheEvict(cacheNames = "agent-cache", key = "'list'")
    })
    public void delete(Long id) {
        getOrThrow(id);
        agentToolMapper.deleteByAgentId(id);
        agentMapper.deleteById(id);
    }

    // ── 详情查询 ─────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = "agent-cache", key = "#id")
    public AgentDetailResponse getDetail(Long id) {
        Agent agent = getOrThrow(id);
        List<Long> toolIds = agentToolMapper.selectMcpServerIdsByAgentId(id);
        return AgentDetailResponse.from(agent, toolIds);
    }

    // ── 列表查询 ─────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = "agent-cache", key = "'list'")
    public Result<PageResult<AgentListItem>> list(AgentQueryRequest request) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
            .eq(request.getEnabled() != null, Agent::getEnabled, request.getEnabled())
            .orderByDesc(Agent::getCreatedAt);

        int pageSize = Math.min(request.getPageSize(), 100);
        var page = agentMapper.selectPage(new Page<>(request.getPage(), pageSize), wrapper);

        List<Long> agentIds = page.getRecords().stream().map(Agent::getId).collect(Collectors.toList());

        // 批量查工具数量，避免 N+1
        Map<Long, Integer> toolCountMap = batchQueryToolCount(agentIds);

        List<AgentListItem> items = page.getRecords().stream()
            .map(agent -> AgentListItem.from(agent, toolCountMap.getOrDefault(agent.getId(), 0)))
            .collect(Collectors.toList());

        return PageResult.of(items, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    // ── 内部工具 ─────────────────────────────────────────────

    private Agent getOrThrow(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
            .eq(Agent::getName, name)
            .ne(excludeId != null, Agent::getId, excludeId);
        if (agentMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.AGENT_NAME_DUPLICATE);
        }
    }

    private void batchInsertTools(Long agentId, List<Long> mcpServerIds) {
        for (Long mcpServerId : mcpServerIds) {
            AgentTool tool = new AgentTool();
            tool.setAgentId(agentId);
            tool.setMcpServerId(mcpServerId);
            agentToolMapper.insert(tool);
        }
    }

    private Map<Long, Integer> batchQueryToolCount(List<Long> agentIds) {
        if (CollectionUtils.isEmpty(agentIds)) {
            return Collections.emptyMap();
        }
        return agentToolMapper.selectList(
            new LambdaQueryWrapper<AgentTool>().in(AgentTool::getAgentId, agentIds)
        ).stream().collect(Collectors.groupingBy(
            AgentTool::getAgentId,
            Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
        ));
    }
}
