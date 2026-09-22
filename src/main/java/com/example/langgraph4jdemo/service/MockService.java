package com.example.langgraph4jdemo.service;

import com.example.langgraph4jdemo.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * @author liushug
 * @description TODO
 */
@Service
public class MockService {

    private final JdbcTemplate authJdbcTemplate;
    private final ObjectMapper objectMapper;

    public MockService(@Qualifier("authJdbcTemplate") JdbcTemplate authJdbcTemplate,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper) {
        this.authJdbcTemplate = authJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public String orderStats(){
        Integer total = authJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders", Integer.class);
        Integer completed = authJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status = 'completed'", Integer.class);
        Integer pending = authJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE status = 'pending'", Integer.class);

        return String.format(
                "本月订单统计：总数 %d，已完成 %d，待处理 %d",
                total, completed, pending);
    }

}
