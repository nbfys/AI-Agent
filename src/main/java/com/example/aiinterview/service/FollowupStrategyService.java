package com.example.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiinterview.entity.FollowupStrategy;
import com.example.aiinterview.mapper.FollowupStrategyMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class FollowupStrategyService {

    @Resource
    private FollowupStrategyMapper followupStrategyMapper;

    public List<FollowupStrategy> getStrategiesByKeyword(String keyword) {
        LambdaQueryWrapper<FollowupStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FollowupStrategy::getKeyword, keyword)
               .orderByAsc(FollowupStrategy::getSortOrder);
        return followupStrategyMapper.selectList(wrapper);
    }

    public FollowupStrategy getStrategyByDimension(String keyword, String dimension) {
        LambdaQueryWrapper<FollowupStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FollowupStrategy::getKeyword, keyword)
               .eq(FollowupStrategy::getDimension, dimension);
        return followupStrategyMapper.selectOne(wrapper);
    }
}
