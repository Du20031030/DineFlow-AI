package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单主表数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     * @return
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 根据订单id查询订单
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 动态更新订单状态、支付状态、取消原因等字段
     * @param orders
     */
    void update(Orders orders);

    /**
     * 查询指定状态且早于指定时间的订单，用于定时任务处理超时订单。
     * @param status
     * @param orderTime
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    /**
     * 根据订单状态统计数量，工作台订单总览使用。
     * status 为 null 时统计全部订单。
     *
     * @param status 订单状态
     * @return 订单数量
     */
    @Select("<script>" +
            "select count(id) from orders " +
            "<where>" +
            "<if test='status != null'>and status = #{status}</if>" +
            "</where>" +
            "</script>")
    Integer countByStatus(Integer status);

    /**
     * 管理端和用户端复用的订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

//    @Param 是 MyBatis 的注解，用来给 Mapper 方法参数起名字，让 XML 里的 #{} 能准确拿到参数。
    /**
     * 根据时间范围和状态汇总订单金额
     * @param begin 开始时间
     * @param end 结束时间
     * @param status 订单状态
     * @return
     */
    Double sumByMap(@Param("begin") LocalDateTime begin,
                    @Param("end") LocalDateTime end,
                    @Param("status") Integer status);

    /**
     * 根据时间范围和状态统计订单数量
     * @param begin 开始时间
     * @param end 结束时间
     * @param status 订单状态
     * @return
     */
    Integer countByMap(@Param("begin") LocalDateTime begin,
                       @Param("end") LocalDateTime end,
                       @Param("status") Integer status);
}
