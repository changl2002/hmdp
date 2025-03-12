package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Component
public class RedisIdCreater {
    public static final long BEGIN_TIME_STAMP = 1735689600L;
    public static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public long createId(String prefix) {
        // 1.计算时间戳
        LocalDateTime now = LocalDateTime.now();
        long secondNow = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = secondNow - BEGIN_TIME_STAMP;
        // 2.生成键
        // 获取当天日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 键的构成为 icr:prefix:date
        String key = "icr:" + prefix + ":" + date;
        // 3.生成序列号
        Long number = stringRedisTemplate.opsForValue().increment(key);
        // 4.拼接序列号和时间戳
        long id = timeStamp << COUNT_BITS | number;
        return id;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
