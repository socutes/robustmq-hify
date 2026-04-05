package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("workflow_run")
public class WorkflowRun extends BaseEntity {
    private Long workflowId;
    /** RUNNING / SUCCESS / FAILED */
    private String status;
    private String input;
    private String output;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime finishedAt;
}
