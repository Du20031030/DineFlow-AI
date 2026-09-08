package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.AgentAddCartDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.AgentCartItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车。
     * 如果当前用户购物车中已经有相同菜品或套餐，则数量加 1；否则插入一条新记录。
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && !list.isEmpty()) {
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cart);
        } else {
            Long dishId = shoppingCartDTO.getDishId();
            if (dishId != null) {
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            } else {
                Long setmealId = shoppingCartDTO.getSetmealId();
                Setmeal setmeal = setmealMapper.getById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 减少购物车中的商品数量。
     * 如果当前商品数量大于 1，则数量减 1；如果数量等于 1，则删除该购物车记录。
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && !list.isEmpty()) {
            ShoppingCart cart = list.get(0);
            Integer number = cart.getNumber();
            if (number == 1) {
                shoppingCartMapper.deleteById(cart.getId());
            } else {
                cart.setNumber(number - 1);
                shoppingCartMapper.updateNumberById(cart);
            }
        }
    }

    /**
     * 查看当前用户购物车。
     */
    @Override
    public List<ShoppingCart> showshoppingCart() {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空当前用户购物车。
     */
    @Override
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }


    /**
     * Agent查询当前用户购物车
     *
     * @param userId 当前登录用户ID
     * @return 购物车商品列表
     */
    @Override
    public List<AgentCartItemVO> getCartForAgent(Long userId) {

        // 1. 构造查询条件，只设置当前用户ID
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();

        // 2. 查询当前用户的所有购物车数据
        List<ShoppingCart> shoppingCartList =
                shoppingCartMapper.list(shoppingCart);

        // 3. ShoppingCart Entity -> AgentCartItemVO
        List<AgentCartItemVO> voList = new ArrayList<>();

        for (ShoppingCart cart : shoppingCartList) {

            AgentCartItemVO vo = AgentCartItemVO.builder()
                    .id(cart.getId())
                    .dishId(cart.getDishId())
                    .setmealId(cart.getSetmealId())
                    .name(cart.getName())
                    .image(cart.getImage())
                    .dishFlavor(cart.getDishFlavor())
                    .number(cart.getNumber())
                    .amount(cart.getAmount())
                    .build();

            voList.add(vo);
        }

        // 4. 返回给Controller
        return voList;
    }

    /**
     * Agent添加商品到购物车
     *
     * @param userId 当前登录用户ID
     * @param agentAddCartDTO 添加购物车参数
     */
    @Override
    public void addCartForAgent(Long userId, AgentAddCartDTO agentAddCartDTO) {

        // 1. 构造购物车查询条件
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .dishId(agentAddCartDTO.getDishId())
                .setmealId(agentAddCartDTO.getSetmealId())
                .dishFlavor(agentAddCartDTO.getDishFlavor())
                .build();

        // 2. 查询购物车中是否已经存在相同商品
        List<ShoppingCart> shoppingCartList =
                shoppingCartMapper.list(shoppingCart);

        if (shoppingCartList != null && !shoppingCartList.isEmpty()) {

            // 3. 已经存在，则数量 + 1
            ShoppingCart cart = shoppingCartList.get(0);

            cart.setNumber(cart.getNumber() + 1);

            shoppingCartMapper.updateNumberById(cart);

        } else {

            // 4. 不存在，则新增购物车记录
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());

            // 5. 如果添加的是菜品
            if (agentAddCartDTO.getDishId() != null) {

                Dish dish =
                        dishMapper.getById(agentAddCartDTO.getDishId());

                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }

            // 6. 如果添加的是套餐
            if (agentAddCartDTO.getSetmealId() != null) {

                Setmeal setmeal =
                        setmealMapper.getById(agentAddCartDTO.getSetmealId());

                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }

            // 7. 插入购物车
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * Agent修改购物车商品数量
     *
     * @param userId 当前用户ID
     * @param cartItemId 购物车记录ID
     * @param number 修改后的数量
     */
    @Override
    public void updateCartItemNumberForAgent(Long userId, Long cartItemId, Integer number) {
        // 1. 校验数量
        if (number == null || number <= 0) {
            throw new RuntimeException("商品数量必须大于0");
        }

        // 2. 修改当前用户对应的购物车记录
        shoppingCartMapper.updateNumberForAgent(userId, cartItemId, number
        );
    }


    /**
     * Agent删除购物车中的单条商品记录
     *
     * @param userId 当前用户ID
     * @param cartItemId 购物车记录ID
     */
    @Override
    public void deleteCartItemForAgent(Long userId, Long cartItemId) {
        shoppingCartMapper.deleteCartItemForAgent(
                userId,
                cartItemId
        );
    }


    /**
     * Agent清空当前用户购物车
     *
     * @param userId 当前用户ID
     */
    @Override
    public void clearCartForAgent(Long userId) {
        shoppingCartMapper.clearCartForAgent(userId);
    }

}
