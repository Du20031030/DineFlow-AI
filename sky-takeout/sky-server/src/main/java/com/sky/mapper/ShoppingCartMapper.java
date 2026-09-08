package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 动态条件查询购物车数据。
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 根据购物车 id 修改商品数量。
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById(ShoppingCart shoppingCart);

    /**
     * 插入购物车数据。
     */
    @Insert("insert into shopping_cart(name, image, user_id, dish_id, setmeal_id, dish_flavor, number, amount, create_time)" +
            " values(#{name}, #{image}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 根据用户 id 清空购物车数据。
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 根据购物车 id 删除一条购物车数据。
     */
    @Delete("delete from shopping_cart where id = #{id}")
    void deleteById(Long id);

    /*
    agent修改购物车数量
    * */
    void updateNumberForAgent(Long userId, Long cartItemId, Integer number);

//    agent删除购物车指定菜品
    void deleteCartItemForAgent(Long userId, Long cartItemId);

//    agent清空购物车
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void clearCartForAgent(Long userId);
}
