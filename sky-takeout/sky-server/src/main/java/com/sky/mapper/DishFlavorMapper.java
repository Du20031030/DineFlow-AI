package com.sky.mapper;


import com.sky.annotation.Autofill;
import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    //批量插入口味
    void insertBatch(List<DishFlavor> flavors, Long id);

    //根据菜品id删除相关联的口味表的数据
    @Delete("delete from dish_flavor where dish_id = #{dish_id}")
    void deleteByDishId(Long dish_id);

    //根据dishIds集合批量删除口味
    void deleteByDishIds(List<Long> dishIds);

    //根据菜品id查询口味数据
    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long dishId);
}
