package com.example.rocketmq.transaction;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务消息监听器实现
 *
 * 两个回调方法：
 *  1. executeLocalTransaction   —— 发送半消息后执行本地事务
 *  2. checkLocalTransaction     —— RocketMQ 回查本地事务状态
 *
 * 场景：订单服务创建订单 + 发送"订单创建"消息
 * 保证：数据库事务与消息发送最终一致
 */
public class TransactionListenerImpl implements TransactionListener {

    /**
     * 本地事务执行记录（模拟数据库中记录事务执行状态）
     * key = 消息的 transactionId（即订单号）
     * value = 执行状态：0=未执行, 1=成功, 2=失败
     */
    private final Map<String, Integer> localTrans = new ConcurrentHashMap<>();

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderId = msg.getTransactionId();
        System.out.println("==== 执行本地事务 ====");
        System.out.println("订单号: " + orderId + ", 参数: " + arg);
        System.out.println("准备扣减库存、写入订单表（数据库事务）...");

        try {
            // ======== 模拟本地数据库事务 ========
            // 1. 开启数据库事务
            //    startTransaction();
            // 2. 执行业务 SQL
            //    INSERT INTO orders(order_no, ...) VALUES(orderId, ...);
            //    UPDATE products SET stock = stock - 1 WHERE id = ...;
            Thread.sleep(100); // 模拟耗时
            // 3. 提交事务 → 此时在真实订单表中已没有订单记录
            //    commitTransaction();

            // 本地事务执行成功
            localTrans.put(orderId, 1);
            System.out.println("本地事务执行成功: " + orderId);
            return LocalTransactionState.COMMIT_MESSAGE;  // 提交消息，消费者立即可见

        } catch (Exception e) {
            // 本地事务失败，回滚消息（消费者不可见）
            localTrans.put(orderId, 2);
            System.err.println("本地事务执行失败: " + e.getMessage());
            return LocalTransactionState.ROLLBACK_MESSAGE;  // 回滚消息
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String orderId = msg.getTransactionId();
        System.out.println("==== RocketMQ 回查本地事务状态 ====");
        System.out.println("订单号: " + orderId);

        // 查询本地事务执行结果（模拟从数据库中按订单号查询）
        Integer status = localTrans.get(orderId);
        if (status == null) {
            // 状态未知（例如本地事务还在执行中），返回 UNKNOW 让 RocketMQ 稍后再次回查
            System.out.println("事务状态未知，稍后重查");
            return LocalTransactionState.UNKNOW;
        }

        if (status == 1) {
            System.out.println("事务成功 → 提交消息");
            return LocalTransactionState.COMMIT_MESSAGE;
        } else {
            System.out.println("事务失败 → 回滚消息");
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }
}