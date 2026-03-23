package com.hify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.entity.AgentTool;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentToolMapper extends BaseMapper<AgentTool> {

    @Select("SELECT mcp_server_id FROM agent_tool WHERE agent_id = #{agentId}")
    List<Long> selectMcpServerIdsByAgentId(Long agentId);

    @Delete("DELETE FROM agent_tool WHERE agent_id = #{agentId}")
    int deleteByAgentId(Long agentId);
}
