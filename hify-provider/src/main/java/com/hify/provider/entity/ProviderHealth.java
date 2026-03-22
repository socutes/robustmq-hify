package com.hify.provider.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 供应商健康状态
 * 高频写入（探测 + 每次调用更新），不继承 BaseEntity，无逻辑删除
 */
@Getter
@Setter
@TableName("provider_health")
public class ProviderHealth {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 provider.id，唯一索引 */
    private Long providerId;

    /**
     * 健康状态
     * UP / DOWN / DEGRADED / UNKNOWN
     */
    private String status;

    /** 最后一次探测时间 */
    private LocalDateTime lastCheckAt;

    /** 最后一次成功时间 */
    private LocalDateTime lastSuccessAt;

    /** 连续失败次数（配合熔断器阈值判断） */
    private Integer failCount;

    /** 最近一次响应延迟（ms） */
    private Integer latencyMs;

    /** 最近失败原因 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
