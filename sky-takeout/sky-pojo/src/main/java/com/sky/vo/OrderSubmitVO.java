package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder //这个注解的作用是 为类提供一个构建器模式的实现，使得可以通过链式调用的方式来创建对象实例。使用@Builder注解后，Lombok会自动生成一个静态内部类Builder，并提供一系列方法来设置类的属性，最后通过build()方法来创建对象实例。这种方式可以提高代码的可读性和可维护性，尤其是在需要创建具有多个属性的对象时。
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmitVO implements Serializable {
    //订单id
    private Long id;
    //订单号
    private String orderNumber;
    //订单金额
    private BigDecimal orderAmount;
    //下单时间
    private LocalDateTime orderTime;
}
