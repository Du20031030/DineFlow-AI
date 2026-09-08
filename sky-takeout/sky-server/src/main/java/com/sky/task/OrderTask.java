package com.sky.task;


import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/*定时任务类
* */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper  orderMapper;

    /*处理超时订单
    * */
    @Scheduled(cron = "0 * * * * ?") //每分钟触发一次
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        if(ordersList!=null && !ordersList.isEmpty()){
            ordersList.forEach(order->{
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("超时未支付");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            });
        }
    }

//    处理一直派送中的订单
    @Scheduled(cron = "0 0 1 * * ?")  //每天凌晨一点触发一次
    public void processDeliverOrder(){
        log.info("定时处理已支付订单：{}", LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusHours(-1);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if(ordersList!=null && !ordersList.isEmpty()){
            ordersList.forEach(order->{
                order.setStatus(Orders.COMPLETED);
                order.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(order);
            });
        }
    }


}
