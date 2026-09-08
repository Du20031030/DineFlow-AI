package com.sky.controller.admin;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@Slf4j
@Api(tags = "管理端店铺相关接口")
@RequestMapping("/admin/shop")
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;

    private static final String Key = "SHOP_STATUS";

    @PutMapping("/{status}")
    @ApiOperation("设置店铺的营业状态")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置店铺的营业状态：{}",status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(Key,status);
        return Result.success();
    }

    @ApiOperation("管理端查询营业状态")
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(Key);
        log.info("获取到店铺的营业状态为：{}",status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }

}
