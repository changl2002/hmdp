package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取锁
        String key=KEY_PREFIX+name;
        // value要加上线程的标识
        String threadId = ID_PREFIX+Thread.currentThread().threadId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        // 避免空指针风险
        return Boolean.TRUE.equals(success);
    }


    @Override
    public void unlock(){
        // 调用lua脚本执行
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().threadId());
    }

    /*@Override
    public void unlock() {
        // 释放锁
        // 获取线程标示

        String key=KEY_PREFIX+name;
        String threadId = ID_PREFIX+Thread.currentThread().threadId();
        // 获取锁中标识
        String id = stringRedisTemplate.opsForValue().get(key);
        if (threadId.equals(id)){
            stringRedisTemplate.delete(key);
        }
    }*/
}
