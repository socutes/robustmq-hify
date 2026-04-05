package com.hify.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.dto.*;
import com.hify.workflow.entity.Workflow;
import com.hify.workflow.entity.WorkflowEdge;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.mapper.WorkflowEdgeMapper;
import com.hify.workflow.mapper.WorkflowMapper;
import com.hify.workflow.mapper.WorkflowNodeMapper;
import com.hify.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final ObjectMapper objectMapper;

    // ── 创建 ────────────────────────────────────────────────────

    @Override
    @Transactional
    public WorkflowDetailVO create(WorkflowCreateRequest req) {
        Workflow wf = new Workflow();
        wf.setName(req.getName());
        wf.setDescription(req.getDescription() != null ? req.getDescription() : "");
        wf.setStatus("DRAFT");
        workflowMapper.insert(wf);

        batchInsertNodes(wf.getId(), req.getNodes());
        batchInsertEdges(wf.getId(), req.getEdges());

        return loadDetail(wf);
    }

    // ── 列表 ────────────────────────────────────────────────────

    @Override
    public Result<PageResult<WorkflowListItem>> list(WorkflowQueryRequest req) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<Workflow>()
                .like(req.getName() != null && !req.getName().isBlank(), Workflow::getName, req.getName())
                .eq(req.getStatus() != null && !req.getStatus().isBlank(), Workflow::getStatus, req.getStatus())
                .orderByDesc(Workflow::getCreatedAt);

        int size = Math.min(req.getPageSize(), 100);
        var p = workflowMapper.selectPage(new Page<>(req.getPage(), size), wrapper);
        List<WorkflowListItem> items = p.getRecords().stream()
                .map(WorkflowListItem::from)
                .collect(Collectors.toList());
        return PageResult.of(items, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    // ── 详情 ────────────────────────────────────────────────────

    @Override
    public WorkflowDetailVO getDetail(Long id) {
        return loadDetail(getOrThrow(id));
    }

    // ── 更新（全量替换节点和边）──────────────────────────────────

    @Override
    @Transactional
    public WorkflowDetailVO update(Long id, WorkflowUpdateRequest req) {
        Workflow wf = getOrThrow(id);
        wf.setName(req.getName());
        if (req.getDescription() != null) wf.setDescription(req.getDescription());
        if (req.getStatus() != null) wf.setStatus(req.getStatus());
        workflowMapper.updateById(wf);

        // 先逻辑删除旧节点和边，再批量插新的
        nodeMapper.update(null,
                new LambdaUpdateWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowId, id)
                        .set(WorkflowNode::getDeleted, 1));
        edgeMapper.update(null,
                new LambdaUpdateWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowId, id)
                        .set(WorkflowEdge::getDeleted, 1));

        batchInsertNodes(id, req.getNodes());
        batchInsertEdges(id, req.getEdges());

        return loadDetail(wf);
    }

    // ── 删除 ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(Long id) {
        getOrThrow(id);
        nodeMapper.update(null,
                new LambdaUpdateWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowId, id)
                        .set(WorkflowNode::getDeleted, 1));
        edgeMapper.update(null,
                new LambdaUpdateWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowId, id)
                        .set(WorkflowEdge::getDeleted, 1));
        workflowMapper.deleteById(id);
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    private Workflow getOrThrow(Long id) {
        Workflow wf = workflowMapper.selectById(id);
        if (wf == null) throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND);
        return wf;
    }

    private void batchInsertNodes(Long workflowId, List<WorkflowNodeReq> reqs) {
        if (CollectionUtils.isEmpty(reqs)) return;
        for (WorkflowNodeReq req : reqs) {
            WorkflowNode node = new WorkflowNode();
            node.setWorkflowId(workflowId);
            node.setNodeKey(req.getNodeKey());
            node.setType(req.getType());
            node.setName(req.getName() != null ? req.getName() : "");
            try {
                node.setConfig(req.getConfig() != null
                        ? objectMapper.writeValueAsString(req.getConfig())
                        : "{}");
            } catch (Exception e) {
                node.setConfig("{}");
            }
            nodeMapper.insert(node);
        }
    }

    private void batchInsertEdges(Long workflowId, List<WorkflowEdgeReq> reqs) {
        if (CollectionUtils.isEmpty(reqs)) return;
        for (WorkflowEdgeReq req : reqs) {
            WorkflowEdge edge = new WorkflowEdge();
            edge.setWorkflowId(workflowId);
            edge.setSourceNodeKey(req.getSourceNodeKey());
            edge.setTargetNodeKey(req.getTargetNodeKey());
            edge.setConditionExpr(req.getCondition());
            edgeMapper.insert(edge);
        }
    }

    private WorkflowDetailVO loadDetail(Workflow wf) {
        List<WorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNode>()
                        .eq(WorkflowNode::getWorkflowId, wf.getId())
                        .orderByAsc(WorkflowNode::getId));
        List<WorkflowEdge> edges = edgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdge>()
                        .eq(WorkflowEdge::getWorkflowId, wf.getId())
                        .orderByAsc(WorkflowEdge::getId));

        List<WorkflowNodeVO> nodeVOs = nodes.stream()
                .map(n -> WorkflowNodeVO.from(n, objectMapper))
                .collect(Collectors.toList());
        List<WorkflowEdgeVO> edgeVOs = edges.stream()
                .map(WorkflowEdgeVO::from)
                .collect(Collectors.toList());

        return WorkflowDetailVO.from(wf, nodeVOs, edgeVOs);
    }
}
