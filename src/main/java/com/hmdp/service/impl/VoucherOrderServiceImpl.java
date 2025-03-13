package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdCreater;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdCreater redisIdCreater;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    // 线程池和任务
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct//当前类初始化完毕以后就开始执行
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    // 线程任务 任务是不断从队列中取出订单下单
    private class VoucherOrderHandler implements Runnable{
        @Override
        //执行任务时机 秒杀开始之前 因为这里需要的是消费者等待 当生产者给出东西的时候要不断获取执行
        //项目启动用户随时可能秒杀
        public void run() {
            while (true){
                try {
                    // 获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建锁对象 这里创建锁对象是因为这是针对id的锁 不同的id锁对象不一样 要尽量缩小锁的范围
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 理论上这里不会再出现并发安全问题了 但做个兜底
        if (!isLock) {
            // 获取锁失败
            log.error("不允许重复下单");
            return;
        }
        try {
            // 那么如果不用proxy调用的话 就是直接当前对象调用 this是非代理对象 是目标对象 没有事务功能
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVolucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        if (r!=0){
            //2.1不为0没有购买资格
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }
        //2.2为0有购买资格 下单信息保存到阻塞队列
        // 生成订单id
        long orderId = redisIdCreater.createId("order");
        // 2.3-2.5存入订单id 用户id 代金券id
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 2.6放入阻塞队列
        orderTasks.add(voucherOrder);//到这里 抢单流程结束
        //3.获取代理对象
        proxy =(IVoucherOrderService) AopContext.currentProxy();
        // 异步下单
        return Result.ok(0);
    }

    /*@Override
    public Result seckillVolucher(Long voucherId) {
        // 查询优惠券
        // 与voucher共享ID 可以直接用
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象 这里创建锁对象是因为这是针对id的锁 不同的id锁对象不一样 要尽量缩小锁的范围
        // SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败
            return Result.fail("不允许重复下单");
        }
        try {
            // 可以拿到当前对象的代理对象
            // 获取事务有关的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 用代理对象来调用
            // 事务要想生效是因为拿到了代理对象
            // 那么如果不用proxy调用的话 就是直接当前对象调用 this是非代理对象 是目标对象 没有事务功能
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }

    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 看是否已经购买过
        Long userId = voucherOrder.getUserId();
        Long count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过了");
            return;
        }
        // 扣减库存 这里用乐观锁来判断库存是否还有剩余
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")//set stock=stock-1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) //where id=? and stock=?
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }
        // 写入数据库 创建订单
        save(voucherOrder);
    }
}
