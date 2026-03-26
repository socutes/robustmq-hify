package com.hify.chat.mapper;

import com.hify.chat.entity.ChatSession;
import com.hify.common.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
