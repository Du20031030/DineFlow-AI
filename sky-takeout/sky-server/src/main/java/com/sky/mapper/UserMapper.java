package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {
//    根据openid查询用户
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

//    插入数据
    void insert(User user);

//    根据userId查询用户
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);

    /**
     * 根据时间范围统计用户数量
     * @param begin 开始时间
     * @param end 结束时间
     * @return
     */
    Integer countByMap(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
