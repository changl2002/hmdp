package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient(){
        // 配置类
        Config config=new Config();
        // 添加Redis地址 这里添加了单点地址 useClusterServers是添加集群地址
        config.useSingleServer().setAddress("redis://192.168.229.128:6379").setPassword("changl2002");
        // 创建客户端
        return Redisson.create(config);
    }
}
