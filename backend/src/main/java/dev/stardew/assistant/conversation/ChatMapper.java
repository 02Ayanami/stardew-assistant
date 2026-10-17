package dev.stardew.assistant.conversation;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChatMapper {
  void create(@Param("id") String id);

  ChatSession session(@Param("id") String id);

  List<ChatSession> history(@Param("limit") int limit, @Param("offset") int offset);

  List<ChatMessage> messages(@Param("id") String id);

  List<ChatMessage> recent(@Param("id") String id);

  void insert(ChatMessage message);

  void touch(@Param("id") String id, @Param("title") String title);
}
