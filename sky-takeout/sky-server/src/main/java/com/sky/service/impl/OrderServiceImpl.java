package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.mq.OrderMessageProducer;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {


    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private ShoppingCartMapper  shoppingCartMapper;

    @Autowired
    private AddressBookMapper  addressBookMapper;

    @Autowired
    private UserMapper  userMapper;

    @Autowired
    private WeChatPayUtil  weChatPayUtil;

    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private OrderMessageProducer orderMessageProducer;

//    提交订单
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

//        处理各种异常情况（地址簿为空，购物车数据为空）
//        地址簿为空
        AddressBook  addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
//            抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

//         查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if(shoppingCartList==null || shoppingCartList.isEmpty()){
//            抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

//        向订单表插入一条数据
        Orders  orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
//        向订单明细表插入多条数据
        shoppingCartList.forEach(cart -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId()); //设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        });

        orderDetailMapper.insertBatch(orderDetailList);
//        清空购物车数据
        shoppingCartMapper.deleteByUserId(userId);
//        封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }


    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 通过RabbitMQ异步触发管理端来单提醒
        // 支付成功后不再直接调用WebSocket，而是先发送订单消息到RabbitMQ。
        // 这样支付主流程只负责更新订单状态，来单提醒由MQ消费者异步处理。
        orderMessageProducer.sendOrderPaidMessage(com.sky.dto.OrderPaidMessageDTO.builder()
                .type(1)
                .orderId(ordersDB.getId())
                .orderNumber(outTradeNo)
                .content("订单号：" + outTradeNo)
                .build());
    }

    /**
     * 用户端历史订单分页查询
     *
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery4User(int page, int pageSize, Integer status) {
        PageHelper.startPage(page, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        Page<Orders> ordersPage = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderVOList = new ArrayList<>();

        if (ordersPage != null && !ordersPage.isEmpty()) {
            for (Orders orders : ordersPage) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDetailList(orderDetailList);
                orderVO.setOrderDishes(getOrderDishesStr(orderDetailList));

                orderVOList.add(orderVO);
            }
        }

        return new PageResult(ordersPage.getTotal(), orderVOList);
    }


//    客户催单
    /**
     * 管理端订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        // PageHelper 会拦截紧随其后的查询，为订单搜索结果自动分页。
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> ordersPage = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderVOList = getOrderVOList(ordersPage);

        return new PageResult(ordersPage.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 管理端顶部订单状态统计只展示待接单、待派送、派送中三个状态。
        Integer toBeConfirmed = orderMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        // 订单详情需要同时返回订单主表信息和订单明细列表。
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setOrderDishes(getOrderDishesStr(orderDetailList));

        return orderVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 只有待接单订单才能被商家接单。
        Orders ordersDB = orderMapper.getById(ordersConfirmDTO.getId());
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 只有待接单订单才能拒单；已支付订单需要先申请退款。
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount());
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .payStatus(Orders.REFUND)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 管理端取消订单不限制原订单状态；已支付订单需要退款。
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (ordersDB.getPayStatus().equals(Orders.PAID)) {
            weChatPayUtil.refund(
                    ordersDB.getNumber(),
                    ordersDB.getNumber(),
                    ordersDB.getAmount(),
                    ordersDB.getAmount());
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .payStatus(Orders.REFUND)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 只有已接单订单才能进入派送中状态。
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();

        orderMapper.update(orders);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> ordersPage) {
        // 订单列表展示需要把每个订单的菜品明细拼成摘要字段 orderDishes。
        List<OrderVO> orderVOList = new ArrayList<>();
        if (ordersPage != null && !ordersPage.isEmpty()) {
            for (Orders orders : ordersPage) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDetailList(orderDetailList);
                orderVO.setOrderDishes(getOrderDishesStr(orderDetailList));

                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    @Override
    public void reminder(Long id) {
//        根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

//        检验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map<String,Object> map = new HashMap<>();
        map.put("type", 2);// 1表示来单提醒,2表示客户催单
        map.put("orderID", ordersDB.getId());
        map.put("content", "订单号：" + ordersDB.getNumber() + "，客户催单");

//        封装成json对象
        String json =  JSONObject.toJSONString(map);
        webSocketServer.sendToAllClient(json);

    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) {
        // 只有派送中订单才能完成，避免跳过接单/派送流程直接完成。
        Orders ordersDB = orderMapper.getById(id);

        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

//        更新订单状态
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.update(orders);
    }


//      订单明细列表转成字符串
    private String getOrderDishesStr(List<OrderDetail> orderDetailList) {
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            return "";
        }
        return orderDetailList.stream()
                .map(orderDetail -> orderDetail.getName() + "*" + orderDetail.getNumber())
                .collect(Collectors.joining(";"));
    }

}
