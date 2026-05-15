package com.rag.mapper;

import com.rag.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    // 根据会话编号查询聊天消息列表
    List<ChatMessage> findBySessionId(@Param("sessionId") Long sessionId);

    // 新增聊天消息
    int insert(ChatMessage message);

    // 根据会话编号删除聊天消息
    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
