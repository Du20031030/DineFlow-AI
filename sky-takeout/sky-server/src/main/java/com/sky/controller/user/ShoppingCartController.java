package com.sky.controller.user;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@Api(tags = "C端-购物车相关接口")
@RequestMapping("/user/shoppingCart")
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加购物车。
     */
    @PostMapping("/add")
    @ApiOperation("添加购物车")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("添加购物车，商品信息为：{}", shoppingCartDTO);
        shoppingCartService.addShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 减少购物车中的商品数量。
     */
    @PostMapping("/sub")
    @ApiOperation("减少购物车商品数量")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("减少购物车商品数量，商品信息为：{}", shoppingCartDTO);
        shoppingCartService.subShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查询当前用户购物车。
     */
    @GetMapping("/list")
    @ApiOperation("查询购物车")
    public Result<List<ShoppingCart>> list(){
        return Result.success(shoppingCartService.showshoppingCart());
    }

    /**
     * 清空当前用户购物车。
     */
    @DeleteMapping("/clean")
    @ApiOperation("清空购物车")
    public Result clean(){
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }
}
