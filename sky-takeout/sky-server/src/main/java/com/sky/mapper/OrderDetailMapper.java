package com.sky.mapper;

import com.sky.entity.OrderDetail;
import com.sky.dto.GoodsSalesDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderDetailMapper {
//    批量插入订单明细数据
    void insertBatch(List<OrderDetail> orderDetailList);
    /**
     * 根据订单id查询订单明细
     * @param orderId
     * @return
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 查询指定时间范围内已完成订单的商品销量前十
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin,
                                      @Param("end") LocalDateTime end,
                                      @Param("status") Integer status);
}
