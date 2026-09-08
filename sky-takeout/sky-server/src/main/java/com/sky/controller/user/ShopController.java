package com.sky.controller.user;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@Slf4j
@Api(tags = "用户端相关接口")
@RequestMapping("/user/shop")
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;

    private static final String Key = "SHOP_STATUS";

    @ApiOperation("用户端查询营业状态")
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        Integer status = (Integer)redisTemplate.opsForValue().get(Key);
        log.info("获取到店铺的营业状态为：{}",status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }


}
