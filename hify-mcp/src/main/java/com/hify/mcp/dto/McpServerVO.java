package com.hify.mcp.dto;

import com.hify.mcp.entity.McpServer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class McpServerVO {

    private Long id;
    private String name;
    private String endpoint;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 连通性测试后填充的工具列表，平时为 null */
    private List<String> tools;

    public static McpServerVO from(McpServer s) {
        McpServerVO vo = new McpServerVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setEndpoint(s.getEndpoint());
        vo.setDescription(s.getDescription());
        vo.setEnabled(s.getEnabled());
        vo.setCreatedAt(s.getCreatedAt());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }
}
