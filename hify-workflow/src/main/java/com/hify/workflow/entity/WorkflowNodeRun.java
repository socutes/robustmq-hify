package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("workflow_node_run")
public class WorkflowNodeRun extends BaseEntity {
    private Long workflowRunId;
    private String nodeKey;
    private String nodeType;
    /** RUNNING / SUCCESS / FAILED */
    private String status;
    /** JSON 快照：ctx.snapshot() */
    private String outputs;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime finishedAt;
}
