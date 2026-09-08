package com.sky.mapper;

import com.sky.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatMessageMapper {


    @Insert("""
    insert into chat_message
    (session_id,user_id,role,content,create_time)
    values
    (#{sessionId},
     #{userId},
     #{role},
     #{content},
     #{createTime})
""")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ChatMessage message);



    @Select("""
        select *
        from chat_message
        where session_id=#{sessionId}
        order by create_time
    """)
    List<ChatMessage> listBySessionId(Long sessionId);


    @Select("""
    select *
    from chat_message
    where session_id=#{sessionId}
    order by create_time desc
    limit #{limit}
    """)
    List<ChatMessage> selectRecentMessages(Long sessionId, Integer limit);


    @Select("""
    select count(*)
    from chat_message
    where session_id=#{sessionId}
    """)
    Integer countBySessionId(Long sessionId);


    /**
     * 删除一个会话下的全部聊天消息
     */
    @Delete("""
        delete from chat_message
        where session_id = #{sessionId}
        """)
    void deleteBySessionId(Long sessionId);


    @Select("""
        select id,
               session_id,
               user_id,
               role,
               content,
               create_time
        from chat_message
        where session_id = (
            select session_id
            from chat_message
            where id = #{messageId}
              and user_id = #{userId}
        )
          and user_id = #{userId}
          and id <= #{messageId}
        order by id asc
        """)
    List<ChatMessage> selectHistoryUntilMessage(
            @Param("userId") Long userId,
            @Param("messageId") Long messageId
    );


}
