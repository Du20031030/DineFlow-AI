package com.sky.service.impl;

import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.vo.AgentDishVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DishServiceImplAgentDetailTest {

    @Test
    void getAgentDishByIdReturnsParsedFlavorOptionsForAgentDetail() {
        DishMapper dishMapper = mock(DishMapper.class);
        DishFlavorMapper dishFlavorMapper = mock(DishFlavorMapper.class);
        DishServiceImpl dishService = new DishServiceImpl();

        ReflectionTestUtils.setField(dishService, "dishMapper", dishMapper);
        ReflectionTestUtils.setField(dishService, "dishFlavorMapper", dishFlavorMapper);

        AgentDishVO dish = AgentDishVO.builder()
                .id(61L)
                .name("test dish")
                .price(new BigDecimal("66"))
                .description("original description")
                .build();
        DishFlavor flavor = DishFlavor.builder()
                .dishId(61L)
                .name("spicy")
                .value("[\"none\",\"mild\",\"medium\",\"hot\"]")
                .build();

        when(dishMapper.getAgentDishById(61L)).thenReturn(dish);
        when(dishFlavorMapper.getByDishId(61L)).thenReturn(Collections.singletonList(flavor));

        AgentDishVO result = dishService.getAgentDishById(61L);

        assertNotNull(result.getFlavors());
        assertEquals(1, result.getFlavors().size());
        assertEquals("spicy", result.getFlavors().get(0).getName());
        assertEquals(Arrays.asList("none", "mild", "medium", "hot"), result.getFlavors().get(0).getOptions());
        assertEquals("original description", result.getDescription());
    }

    @Test
    void getAgentDishByIdKeepsDetailWhenFlavorOptionsCannotBeParsed() {
        DishMapper dishMapper = mock(DishMapper.class);
        DishFlavorMapper dishFlavorMapper = mock(DishFlavorMapper.class);
        DishServiceImpl dishService = new DishServiceImpl();

        ReflectionTestUtils.setField(dishService, "dishMapper", dishMapper);
        ReflectionTestUtils.setField(dishService, "dishFlavorMapper", dishFlavorMapper);

        AgentDishVO dish = AgentDishVO.builder()
                .id(61L)
                .name("test dish")
                .price(new BigDecimal("66"))
                .description("original description")
                .build();
        DishFlavor flavor = DishFlavor.builder()
                .dishId(61L)
                .name("spicy")
                .value("medium")
                .build();

        when(dishMapper.getAgentDishById(61L)).thenReturn(dish);
        when(dishFlavorMapper.getByDishId(61L)).thenReturn(Collections.singletonList(flavor));

        AgentDishVO result = dishService.getAgentDishById(61L);

        assertNotNull(result);
        assertEquals("original description", result.getDescription());
        assertEquals(1, result.getFlavors().size());
        assertEquals(Collections.emptyList(), result.getFlavors().get(0).getOptions());
    }
}
