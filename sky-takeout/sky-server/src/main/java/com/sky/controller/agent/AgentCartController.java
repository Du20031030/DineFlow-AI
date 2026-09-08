package com.sky.controller.agent;

import com.sky.dto.AgentAddCartDTO;
import com.sky.dto.AgentUpdateCartDTO;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import com.sky.vo.AgentCartItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提供给 Python Agent 调用的购物车接口
 */
@RestController
@RequestMapping("/internal/agent/cart")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "Agent端-购物车相关接口")
public class AgentCartController {

    private final ShoppingCartService shoppingCartService;

    /**
     * 查询指定用户的购物车
     */
    @GetMapping("/{userId}")
    @ApiOperation("Agent查询指定用户的购物车")
    public Result<List<AgentCartItemVO>> getCart(@PathVariable Long userId) {
        log.info("Agent查询用户购物车，userId：{}", userId);

        List<AgentCartItemVO> cartList =
                shoppingCartService.getCartForAgent(userId);

        return Result.success(cartList);
    }

    /**
     * Agent添加商品到购物车
     */
    @ApiOperation("Agent添加商品到购物车")
    @PostMapping("/{userId}/items")
    public Result<String> addToCart(@PathVariable Long userId, @RequestBody AgentAddCartDTO agentAddCartDTO) {
        log.info(
                "Agent添加商品到购物车，userId：{}，dishId：{}，setmealId：{}，dishFlavor：{}",
                userId,
                agentAddCartDTO.getDishId(),
                agentAddCartDTO.getSetmealId(),
                agentAddCartDTO.getDishFlavor()
        );

        shoppingCartService.addCartForAgent(userId, agentAddCartDTO);
        return Result.success("添加购物车成功");
    }
    /*
    * agent更新购物车商品数量
    * */
    @ApiOperation("Agent更新购物车商品数量")
    @PatchMapping("/{userId}/items/{cartItemId}")
    public Result updateCartItemNumber(@PathVariable Long userId, @PathVariable Long cartItemId,
                                       @RequestBody AgentUpdateCartDTO dto) {

        shoppingCartService.updateCartItemNumberForAgent(userId, cartItemId, dto.getNumber()
        );

        return Result.success();
    }


//    agent删除购物车
    @DeleteMapping("/{userId}/items/{cartItemId}")
    public Result deleteCartItem(@PathVariable Long userId, @PathVariable Long cartItemId) {

        shoppingCartService.deleteCartItemForAgent(
                userId,
                cartItemId
        );

        return Result.success();
    }


//    agent清空购物车
    @DeleteMapping("/{userId}")
    public Result clearCart(@PathVariable Long userId) {
        shoppingCartService.clearCartForAgent(userId);
        return Result.success();
    }

}