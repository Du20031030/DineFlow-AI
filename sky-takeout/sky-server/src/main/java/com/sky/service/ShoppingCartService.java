package com.sky.service;

import com.sky.dto.AgentAddCartDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.vo.AgentCartItemVO;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加购物车。
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 减少购物车中的商品数量。
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看当前用户购物车。
     */
    List<ShoppingCart> showshoppingCart();

    /**
     * 清空当前用户购物车。
     */
    void cleanShoppingCart();

    /**
     * Agent查询当前用户购物车
     *
     * @param userId 当前登录用户ID
     * @return 购物车商品列表
     */
    List<AgentCartItemVO> getCartForAgent(Long userId);

    /**
     * Agent添加商品到购物车
     *
     * @param userId 当前登录用户ID
     * @param agentAddCartDTO 添加购物车参数
     */
    void addCartForAgent(Long userId, AgentAddCartDTO agentAddCartDTO);


    /**
     * Agent修改购物车商品数量
     *
     * @param userId 当前用户ID
     * @param cartItemId 购物车记录ID
     * @param number 修改后的数量
     */
    void updateCartItemNumberForAgent(Long userId, Long cartItemId, Integer number);


    /**
     * Agent删除购物车中的单条商品记录
     *
     * @param userId 当前用户ID
     * @param cartItemId 购物车记录ID
     */
    void deleteCartItemForAgent(Long userId, Long cartItemId);


    /**
     * Agent清空当前用户购物车
     *
     * @param userId 当前用户ID
     */
    void clearCartForAgent(Long userId);
}
