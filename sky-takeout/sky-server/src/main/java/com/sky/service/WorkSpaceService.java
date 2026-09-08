package com.sky.service;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;

/**
 * 工作台业务服务。
 */
public interface WorkSpaceService {

    /**
     * 查询今日经营数据。
     *
     * @return 今日营业额、有效订单数、完成率、客单价和新增用户数
     */
    BusinessDataVO getBusinessData();

    /**
     * 查询订单总览数据。
     *
     * @return 各订单状态数量和订单总数
     */
    OrderOverViewVO getOrderOverView();

    /**
     * 查询菜品总览数据。
     *
     * @return 起售和停售菜品数量
     */
    DishOverViewVO getDishOverView();

    /**
     * 查询套餐总览数据。
     *
     * @return 起售和停售套餐数量
     */
    SetmealOverViewVO getSetmealOverView();
}
