package com.sky.controller;

import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestToken {

    @Autowired
    private JwtProperties jwtProperties;


    @GetMapping("/token/{userId}")
    public String testToken(@PathVariable Long userId){

        Map<String,Object> claims = new HashMap<>();
        claims.put("userId", userId);

        return JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );
    }
}