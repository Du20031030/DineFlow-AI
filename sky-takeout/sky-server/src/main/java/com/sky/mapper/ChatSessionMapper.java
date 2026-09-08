package com.sky.mapper;

import com.sky.entity.ChatSession;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper {

    /**
     * 根据会话id查询会话信息
     *
     * 用于加载：
     * 1. 会话摘要 summary
     * 2. 用户校验
     */
    @Select("select * from chat_session where id=#{id}")
    ChatSession selectById(Long id);


    @Insert("""
        insert into chat_session
        (user_id,title,create_time,update_time)
        values
        (#{userId},
         #{title},
         #{createTime},
         #{updateTime})
    """)
    @Options(useGeneratedKeys = true,keyProperty = "id")
    void insert(ChatSession session);


    /**
     * 更新会话摘要
     *
     * 用于短期记忆压缩：
     * 当历史消息过多时，
     * 将旧消息总结成summary保存
     */
    @Update(" update chat_session set summary=#{summary}, update_time=#{updateTime} where id=#{id}")
    void updateSummary(ChatSession session);


    /**
     * 更新会话标题
     */
    @Update("""
        update chat_session
        set title = #{title},
            update_time = #{updateTime}
        where id = #{sessionId}
        """)
    void updateTitle(
            @Param("sessionId") Long sessionId,
            @Param("title") String title,
            @Param("updateTime") LocalDateTime updateTime
    );


    @Update("""
        update chat_session
        set update_time = #{updateTime}
        where id = #{sessionId}
        """)
    void updateTime(
            @Param("sessionId") Long sessionId,
            @Param("updateTime") LocalDateTime updateTime
    );


    @Update("""
        update chat_session
        set title = #{title}
        where id = #{sessionId}
          and user_id = #{userId}
        """)
    int updateTitleByUser(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("title") String title
    );

    /**
     * 查询当前用户的全部会话
     *
     * 按最近活跃时间倒序排列
     */
    @Select("""
        select id,
               user_id,
               title,
               create_time,
               update_time,
               summary
        from chat_session
        where user_id = #{userId}
        order by update_time desc
        """)
    List<ChatSession> listByUserId(Long userId);

    /**
     * 删除当前用户自己的会话
     */
    @Delete("""
        delete from chat_session
        where id = #{sessionId}
          and user_id = #{userId}
        """)
    int deleteByIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );

}