package com.gytrinket.gytrinket.engine.bus;

/**
 * 订阅句柄，用于注销或查询是否仍活跃。
 */
public interface Subscription {

    void unsubscribe();

    boolean isActive();
}
