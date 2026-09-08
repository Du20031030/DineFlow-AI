package com.sky.controller.agent;

import com.sky.dto.AgentSetmealSearchDTO;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.AgentSetmealSummaryVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/internal/agent/setmeals")
public class AgentSetmealController {

    @Autowired
    private SetmealService  setmealService;

    /**
     * Agent查询真实在售套餐
     */
    @PostMapping("/search")
    public Result<List<AgentSetmealSummaryVO>> search(@RequestBody AgentSetmealSearchDTO agentSetmealSearchDTO) {
        log.info("Agent查询套餐：{}", agentSetmealSearchDTO);
        List<AgentSetmealSummaryVO> list =
                setmealService.searchSetmealsForAgent(agentSetmealSearchDTO);
        return Result.success(list);
    }

}
